(ns vmfest.virtualbox.conditions
  (:use clojure.contrib.condition)
  (:require [clojure.contrib.logging :as log])
  (:import [org.virtualbox_3_2
            InvalidObjectFault
            InvalidObjectFaultMsg
            RuntimeFault
            RuntimeFaultMsg]))

(defn unsigned-int-to-long [ui]
  (bit-and (long ui) 0xffffffff))

;; from http://forums.virtualbox.org/viewtopic.php?f=7&t=30273
(defonce *error-code-map*
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
          (log/warn (format "Processing exception %s as a java.lang.Exception. Cause %s" (class this) (.getCause this)))
          {:original-message (.getMessage this)
           :cause (.getCause this)
           :type :exception
           })
  java.net.ConnectException 
  (as-map [this]
          {:type :connection-error})
  com.sun.xml.internal.ws.client.ClientTransportException
  (as-map [this]
          {:type :connection-error})
  RuntimeFaultMsg
  (as-map [this]
          (let [message (.getMessage this)
                info (try (.getFaultInfo this)
                        (catch Exception e)) ;; runtime fault
                interface-id (when info (.getInterfaceID info))
                component (when info (.getComponent info))
                result-code (when info (unsigned-int-to-long (int (.getResultCode info)))) ;; originally an unsigned int
                text (when info (.getText info))]
            {:type :vbox-runtime
             :original-message message
             :origin-id interface-id
             :origin-component component
             :error-code result-code
             :original-error-type (*error-code-map* result-code)
             :text text}))
  InvalidObjectFaultMsg
  (as-map [this]
          {:type :vbox-invalid-object
           :original-message (.getMessage this)
           :bad-object-id (.getBadObjectId this)}))

(defn condition-from-webservice-exception [e]
  (let [cause (.getCause e)]
    (if cause
      (as-map cause)
      (as-map e))))

(defn log-and-raise [exception optional-keys]
  (let [log-level (or (:log-level optional-keys) :error)
        message (or (:message optional-keys) "An exception occurred.")
        full-message (str message ": " (.getMessage exception))]
    (log/log log-level message)
    (raise (merge {:message full-message
                   :cause exception
                   :stack-trace (stack-trace-info exception)}
                  (condition-from-webservice-exception exception)
                  optional-keys))))

(defn wrap-vbox-runtime [e error-condition-map & default-condition-map]
  (let [condition (condition-from-webservice-exception e)
        error-type (:original-error-type condition)
        condition-map (error-type error-condition-map) ;; the map corresponding to the error type
        merged-condition (merge default-condition-map condition-map)]
    (log-and-raise e merged-condition)))

(defn handle-vbox-runtime* [condition type-action-map]
  (let [error-type (:original-error-type condition)
        action (error-type type-action-map)]
    (if action action
        (raise condition))))

(defmacro handle-vbox-runtime [type-action-map]
  `(handle-vbox-runtime* *condition* ~type-action-map))

(defn re-log-and-raise* [condition optional-keys]
  (log-and-raise (:cause condition)
                 (merge condition optional-keys)))

(defmacro re-log-and-raise [optional-keys]
  `(re-log-and-raise* *condition* ~optional-keys))

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