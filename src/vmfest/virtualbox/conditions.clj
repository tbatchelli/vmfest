(ns vmfest.virtualbox.conditions
  (:use [clojure.contrib.logging :as log]
        clojure.contrib.condition))

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

(defn condition-from-webservice-exception [e]
  (when (instance? javax.xml.ws.WebServiceException e)
    (let [rfm (.getCause e) ;;runtime fault message
          message (.getMessage rfm)
          rf (.getFaultInfo rfm) ;; runtime fault
          interface-id (.getInterfaceID rf)
          component (.getComponent rf)
          result-code (unsigned-int-to-long (int (.getResultCode rf))) ;; originally an unsigned int
          text (.getText rf)]
      {:original-message message
       :origin-id interface-id
       :origin-component component
       :error-code result-code
       :original-error-type (*error-code-map* result-code)
       :text text})))

(defn log-and-raise [exception log-level message type & kvs]
  (let [optional-keys (apply hash-map kvs)
        full-message (str message ":" (.getMessage exception))]
    (log/log log-level message)
    (raise (merge (assoc optional-keys :type type
                         :message full-message
                         :cause exception)
                  (condition-from-webservice-exception exception)))))
