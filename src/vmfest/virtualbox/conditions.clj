(ns vmfest.virtualbox.conditions
  (:use [clojure.stacktrace :only [root-cause]]
        [slingshot.slingshot :only [try+ throw+]]
        [vmfest.virtualbox.version :only [evaluate-when]])
  (:require [clojure.tools.logging :as log])
  (:import [org.virtualbox_4_3 VBoxException]))

(defn unsigned-int-to-long [ui]
  (bit-and (long ui) 0xffffffff))

;; from http://forums.virtualbox.org/viewtopic.php?f=7&t=30273
(defonce error-code-map
  {0 :VBOX_E_UNKNOWN,
   2159738881 :VBOX_E_OBJECT_NOT_FOUND,
   2159738882 :VBOX_E_INVALID_VM_STATE,
   2159738883 :VBOX_E_VM_ERROR,
   2159738884 :VBOX_E_FILE_ERROR,
   2159738885 :VBOX_E_IPRT_ERROR,
   2159738886 :VBOX_E_PDM_ERROR,
   2159738887 :VBOX_E_INVALID_OBJECT_STATE,
   2159738888 :VBOX_E_HOST_ERROR,
   2159738889 :VBOX_E_NOT_SUPPORTED,
   2159738890 :VBOX_E_XML_ERROR,
   2159738891 :VBOX_E_INVALID_SESSION_STATE,
   2159738892 :VBOX_E_OBJECT_IN_USE,
   2147942405 :E_ACCESSDENIED,
   2147500035 :E_POINTER,
   2147500037 :E_FAIL,
   2147500033 :E_NOTIMPL,
   2147942414 :E_OUTOFMEMORY,
   2147942487 :E_INVALIDARG,
   2147549183 :E_UNEXPECTED})

(defprotocol fault
  (as-map [this]))

(extend-protocol fault
  java.lang.Exception
  (as-map [this]
    (log/warnf
     "Processing exception %s as a java.lang.Exception. Cause %s"
     (class this)
     (root-cause this))
    (log/debugf (root-cause this) "Ex")
    {:original-message (.getMessage this)
     :cause (root-cause this)
     :type :exception})
  java.net.ConnectException
  (as-map [this]
    {:type :connection-error})
  com.sun.xml.internal.ws.client.ClientTransportException
  (as-map [this]
    {:type :connection-error})
  VBoxException
  (as-map [this]
    (let [message (.getMessage this)
          wrapped (.getWrapped this)]
      (merge
       (when wrapped (as-map wrapped))
       {:message message}))))

(evaluate-when
 :ws
 (import '[org.virtualbox_4_3.jaxws
           InvalidObjectFaultMsg
           RuntimeFaultMsg
           RuntimeFault])
 (extend-protocol fault
   RuntimeFaultMsg
   (as-map [this]
     (let [message (.getMessage this)
           ^RuntimeFault info
           (try (.getFaultInfo this)
                (catch Exception e)) ;; runtime fault
           interface-id (when info (.getInterfaceID info))
           component (when info (.getComponent info))
           result-code (when info
                         (unsigned-int-to-long
                          (int (.getResultCode info))))
           text (when info (.getText info))]
       {:type :vbox-runtime
        :original-message message
        :origin-id interface-id
        :origin-component component
        :error-code result-code
        :original-error-type (error-code-map result-code)
        :text text}))
   InvalidObjectFaultMsg
   (as-map [this]
     {:type :vbox-invalid-object
      :original-message (.getMessage this)})))

(defn condition-from-exception [^Exception e]
  (try
    (if-let [cause (try (.getWrapped ^VBoxException e) (catch Exception _))]
      (let [condition (as-map cause)]
        (log/debug (format "formatting wrapped exception %s" condition))
        condition)
      (let [condition (as-map e)]
        (log/debug (format "formatting exception %s" condition))
        condition))
    (catch Exception e
      (log/errorf
       "Cannot parse the error since the object is unavailable %s" e)
      {})))

(defn wrap-exception [^Exception exception optional-keys]
  (try+
    (let [message (or (:message optional-keys) "An exception occurred.")
          full-message (format "%s: %s" message (.getMessage exception))]
      (throw+ (merge {:full-message full-message
                      :cause exception
;;                      :stack-trace nil  ;; redundant
                      }
                     (condition-from-exception exception)
                     optional-keys)))
    (catch Exception e
      (log/error "condition: Cannot process exception" e)
      (throw+ e))))

(defn wrap-vbox-exception [e error-condition-map & default-condition-map]
  (if (instance? VBoxException e)
    (let [condition (condition-from-exception e)
          error-type (:original-error-type condition)
          ;; HACK... error type should always exist
          condition-map (when error-type (error-type error-condition-map))
          merged-condition (merge default-condition-map condition-map)]
      (when-not error-type
        (log/error
         (format
          "conditions: This VBoxException does not have an error type %s" e)))
      (wrap-exception e merged-condition))
    (wrap-exception e {})))

(defn handle-vbox-runtime* [condition type-action-map]
  (let [error-type (:original-error-type condition)
        action (error-type type-action-map)]
    (if action action
        (throw+ condition))))

(defmacro handle-vbox-runtime [type-action-map]
  `(handle-vbox-runtime* *condition* ~type-action-map))

(defn re-log-and-raise* [condition optional-keys]
  (wrap-exception (:cause condition)
                 (merge condition optional-keys)))

(defmacro re-log-and-raise [optional-keys]
  `(re-log-and-raise* *condition* ~optional-keys))

(defn message-map-to-condition-map [message-map]
  (into {}
        (map (fn [[k v]]
               (if (map? v)
                 {k v}
                 {k {:message v}}))
             message-map)))

(defmacro with-vbox-exception-translation
  "Runs the code in boxy and captures any exception thrown, providing
a new exception with a message based on the provider
`type-to-condition-map`. This allows wrapping virtualbox in clojure
without leaking the underlying abstraction via exceptions.

The inteneded use of this macro is wrapping a single virtualbox call.
The possible error codes that a call to virtualbox can issue are
described in the documentation.

e.g:

 (conditions/with-vbox-exception-translation
      {:E_INVALIDARG
       \"SATA device, SATA port, IDE port or IDE slot out of range.\"
       :VBOX_E_INVALID_OBJECT_STATE
       \"Attempt to attach medium to an unregistered virtual machine.\"
       :VBOX_E_INVALID_VM_STATE
       \"Invalid machine state.\"
       :VBOX_E_OBJECT_IN_USE
       \"Hard disk already attached to this or another virtual machine.\"}
      (.attachDevice m
                     name
                     (Integer. controller-port)
                     (Integer. device)
                     type
                     medium))"
  [type-to-condition-map & body]
  `(try
     ~@body
     (catch VBoxException e#
       (conditions/wrap-vbox-exception
        e#
        (message-map-to-condition-map ~type-to-condition-map)))))

(comment
  ;; error handling using conditions
  (use 'vmfest.virtualbox.virtualbox)
  (def my-server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" "") )
  (def my-no-machine (vmfest.virtualbox.model.Machine. "bogus" my-server nil)) ;; a bogus machine

  (use 'vmfest.virtualbox.machine)
  (use 'clojure.contrib.condition)
  (require '[vmfest.virtualbox.conditions :as conditions])
  ;; handle error based on original error type
  (handler-case :type
    (start my-no-machine)
    (handle :connection-error (println "Server not started or wrong url "))
    (handle :vbox-runtime
      (conditions/handle-vbox-runtime {:VBOX_E_OBJECT_NOT_FOUND "The machine does not exist!"
                           :VBOX_E_HOST_ERROR "Something happened!"})))
  ;; ->The machine does not exist!

  (handler-case :type
    (handler-case :type
      (start my-no-machine)
      (handle :connection-error
        (println "Server not started or wrong url "))
      (handle :vbox-runtime
        (conditions/handle-vbox-runtime {:VBOX_E_OBJECT_NOT_FOUND
                              (conditions/re-log-and-raise {:warn "nah! not that bad!" :type :new-type})
                              :VBOX_E_HOST_ERROR "Something happened!"})))
    (handle :new-type "Nothing to see here, move along..." ))
  )
