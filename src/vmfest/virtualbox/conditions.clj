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
   2147500033 :VBOX_E_NOTIMPL,
   2159738882 :VBOX_E_INVALID_VM_STATE,
   2159738883 :VBOX_E_VM_ERROR,
   2147500035 :VBOX_E_POINTER,
   2159738884 :VBOX_E_FILE_ERROR,
   2147942405 :VBOX_E_ACCESSDENIED,
   2159738885 :VBOX_E_IPRT_ERROR,
   2147500037 :VBOX_E_FAIL,
   2159738886 :VBOX_E_PDM_ERROR,
   2159738887 :VBOX_E_INVALID_OBJECT_STATE,
   2159738888 :VBOX_E_HOST_ERROR,
   2159738889 :VBOX_E_NOT_SUPPORTED,
   2159738890 :VBOX_E_XML_ERROR,
   2159738891 :VBOX_E_INVALID_SESSION_STATE,
   2159738892 :VBOX_E_OBJECT_IN_USE,
   2147942414 :VBOX_E_OUTOFMEMORY,
   2147942487 :VBOX_E_INVALIDARG,
   2147549183 :VBOX_E_UNEXPECTED})

(defprotocol fault
  (as-map [this]))

(extend-protocol fault
  java.lang.Throwable
  (as-map [this]
          (log/debug (format "Processing exception %s as a java.lang.Throwable. Cause %s" (class this) (.getCause this)))
          {:original-message (.getMessage this)
           :cause (.getCause this)
           :type :runtime})
  RuntimeFaultMsg
  (as-map [this]
          (let [message (.getMessage this)
                info (try (.getFaultInfo this)
                        (catch Exception e)) ;; runtime fault
                interface-id (when info (.getInterfaceID info))
                component (when info (.getComponent info))
                result-code (when info (unsigned-int-to-long (int (.getResultCode info)))) ;; originally an unsigned int
                text (when info (.getText info))]
            {:original-message message
             :origin-id interface-id
             :origin-component component
             :error-code result-code
             :original-error-type (*error-code-map* result-code)
             :text text}))
  InvalidObjectFaultMsg
  (as-map [this]
          (let [bad-object-id (.getBadObjectId this)]
            {:original-message (.getMessage this)
             :bad-object-id bad-object-id})))

(defn condition-from-webservice-exception [e]
  (as-map (.getCause e)))

(defn log-and-raise [exception log-level message type & kvs]
  (let [optional-keys (apply hash-map kvs)
        full-message (str message ":" (.getMessage exception))]
    (log/log log-level message)
    (raise (merge (assoc optional-keys :type type
                         :message full-message
                         :cause exception)
                  (condition-from-webservice-exception exception)))))

(comment
  ;; error handling using conditions
  (use 'vmfest.virtualbox.virtualbox)
  (def my-server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" "") )
  (def my-no-machine (vmfest.virtualbox.model.Machine. "bogus" my-server nil)) ;; a bogus machine

  (use 'vmfest.virtualbox.machine)
  (use 'clojure.contrib.condition)
  ;; handle error based on original error type
  (handler-case :original-error-type 
                   (start my-no-machine)
                   (handle :VBOX_E_OBJECT_NOT_FOUND (println "No such machine exists ")))
  ;; -> No such machine exists
  )