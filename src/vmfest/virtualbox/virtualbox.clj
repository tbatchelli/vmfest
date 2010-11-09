(ns vmfest.virtualbox.virtualbox
  (:use [clojure.contrib.logging :as log]
        clojure.contrib.condition
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox]))


(defn ^IWebsessionManager create-session-manager
   "Creates a IWebsessionManager. Note that the default port is 18083"
   [url]
   (log/debug (str "Creating session manager for " url))
   (IWebsessionManager. url))

(defn ^IVirtualBox create-vbox
 "TODO"
 ([^IWebsessionManager mgr username password]
    (log/debug (str "creating new vbox with a logon for user " username))
    (try 
      (.logon mgr username password)
      (catch com.sun.xml.internal.ws.client.ClientTransportException e
        (let [message (format "Cannot connect to virtualbox server: '%s'" (.getMessage e))]
          (log/error message)
          (raise :type :connection-error :message message)))))
 ([^model/host host]
    (let [{:keys [url username password]} host
          mgr (create-session-manager url)]
      (create-vbox mgr username password))))

(defn create-mgr-vbox
  ([server]
     (let [{:keys [url username password]} server]
       (create-mgr-vbox url username password)))
  ([url username password]
     (let [mgr (create-session-manager url)
           vbox (create-vbox mgr username password)]
       [mgr vbox])))

(defn find-machine
  "Finds where a machine exists from either its ID or its name. Returns
the IMachine corresponding to the supplied name or ID, or null if such
machine cannot be found.

vbox - A VirtualBox
vm-string A String with either the ID or the name of the machine to find"
  [vbox vm-string]
  (try
    (.getMachine vbox vm-string)
       (catch Exception e
         (try
           (.findMachine vbox vm-string)
              (catch Exception e
                (log/warn (format "There is no machine identified by '%s'" vm-string)))))))

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

(defmacro with-direct-session
  [vb-m names & body]
  (let [[session machine] names]
    `(try
       (let [machine-id# (:id ~vb-m)
             [mgr# vbox#] (create-mgr-vbox (:server ~vb-m))]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (.openSession vbox# ~session machine-id#)
           (log/trace (format "direct session is open for machine-id='%s'" machine-id#))
           (let [~machine (.getMachine ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (log-and-raise e#
                                :error
                                (format "Called a method that is not available with a direct session in '%s'" '~body)
                                :invalid-method))))))
       (catch Exception e# 
         (log-and-raise e# :error
                        (format "Cannot open session with machine '%s' reason:%s"
                                   (:id ~vb-m)
                                   (.getMessage e#))
                        :connection-error)))))

(defmacro with-no-session
  [^model/machine vb-m names & body]
  (let [machine (first names)]
    `(try
       (let [[mgr# vbox#] (create-mgr-vbox (:server ~vb-m))
             ~machine (find-machine vbox# (:id ~vb-m))]
         ~@body)
       (catch java.lang.IllegalArgumentException e#
         (log-and-raise e# :error "Called a method that is not available without a session"
                        :method-not-available))
       (catch Exception e#
         (log-and-raise e# :error "An error occurred" :unknown)))))

(defmacro with-remote-session
  [^model/machine vb-m names & body]
  (let [[session console] names]
    `(try
       (let [machine-id# (:id ~vb-m)
             [mgr# vbox#] (create-mgr-vbox (:server ~vb-m))]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (.openExistingSession vbox# ~session machine-id#)
           (trace (str "new remote session is open for machine-id=" machine-id#))
           (let [~console (.getConsole ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (log-and-raise e# :error "Called a method that is not available without a session"
                                :method-not-available))))))
       (catch Exception e#
         (log-and-raise e# :error "An error occurred" :unknown)))))

(defn start
  [^model/machine vb-m]
  (let [machine-id (:id vb-m)
        [mgr vbox] (create-mgr-vbox (:server vb-m))
        session (.getSessionObject mgr vbox)
        session-type  "gui"
        env "DISPLAY:0.0"]
    (try (let [progress (.openRemoteSession vbox session machine-id session-type env)]
           (debug (str "Starting session for VM " machine-id "..."))
           (.waitForCompletion progress 10000)
           (let [result-code (.getResultCode progress)]
             (if (zero? result-code)
               nil
               true)))
         (catch Exception e#
           (log-and-raise e# :error "An error occurred" :unknown)))))

(defn stop 
  [^model/machine m]
  (with-remote-session m [_ machine]
    (.powerButton machine)))

(defn pause
  [^model/machine m]
  (with-remote-session m [_ machine]
    (.pause machine)))

(defn resume
  [^model/machine m]
  (with-remote-session m [_ machine]
    (.resume machine)))
;;;;;;;

(comment
  (use 'vmfest.virtualbox.virtualbox)
  
  ;; this is only necessary to obtain the machine-id for the target virtual machine
  (def mgr (create-session-manager "http://localhost:18083"))
  (def vbox (create-vbox mgr "" ""))
  (def machine-id (.getId (find-machine vbox "CentOS Minimal")))

  ;; this is the only code actually
  ;; needed to configure the virtual
;; machine
  (def server (vmfest.virtualbox.model.server. "http://localhost:18083" "" ""))
  (def vb-m (vmfest.virtualbox.model.machine. machine-id server nil))
  
  ;; read config values with a session
  (with-direct-session vb-m [session machine]
    (.getMemorySize machine))

  ;; set config values
  (with-direct-session vb-m [session machine]
    (.setMemorySize machine (long 2048))
    (.saveSettings machine))

  ;; start a vm
  (start vb-m)

  ;; stop a vm (or control it)
  (with-remote-session vb-m [session machine]
    (.powerDown machine))

  ;; read config values without a session
  (with-no-session vb-m [machine]
    (.getMemorySize machine))

  ;; error handling using conditions
  (def my-no-machine
       (build-vbox-machine "http://localhost:18083" "" "" "bogus")) ;; a bogus machine

  ;; handle error based on original error type
  (handler-case :original-error-type 
                   (start my-no-machine)
                   (handle :VBOX_E_OBJECT_NOT_FOUND (println "No such machine exists ")))
  ;; -> No such machine exists
  )
