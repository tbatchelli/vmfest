(ns vmfest.core
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox
            ISession
            IMachine]))

(defn ^IWebsessionManager create-session-manager [host & port]
  (let [port-number (or port 18083)
        url (str "http://" host ":" port-number)]
    (println "Creating session manager for" url)
    (IWebsessionManager. url)))

(defn ^IVirtualBox create-vbox [^IWebsessionManager service-manager username password]
  (.logon service-manager username password))

(defn vm-to-map [vm])

(defn vm-list [^IVirtualBox vbox]
  (.getMachines vbox))

(defn print-vm [vm]
  (print (.getName vm)))

(defn start-machine [^IVirtualBox vbox ^IMachine machine ^ISession session]
  (let [uuid (.getId machine)
        session-type "gui"
        env "DISPLAY:0.0"
        progress (.openRemoteSession vbox session uuid session-type env)]
    ( println "Session for VM" uuid "is opening...")
    (.waitForCompletion progress 10000)
    (let [result-code (.getResultCode progress)]
      (if (zero? result-code)
        nil
        true))))

(defn find-machine [vbox vm-string]
  (try (.getMachine vbox vm-string)
       (catch Exception e
         (try (.findMachine vbox vm-string)
              (catch Exception e nil)))))

(defn start-vm [^IWebsessionManager mgr ^IVirtualBox vbox vm-string]
  (let [^ISession session (.getSessionObject mgr vbox)
        machine (find-machine vbox vm-string)]
    (when machine
      (start-machine vbox machine session))
    (.close session))) ; todo: wrap the above in a try-catch an make
                       ; sure the session is always closed

(comment
  "execute this at the command line to disable password checking
   $ VBoxManage setproperty websrvauthlibrary null
   execute this at the command line to start server
   $ vboxwebsrv -v"
  (def mgr (create-session-manager "localhost"))
  (def vbox (create-vbox mgr "test" "test"))
  (map print-vm (vm-list vbox)) ; print available images
  (start-vm mgr vbox "CentOS Test"))