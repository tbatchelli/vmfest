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


(defn machine [^IWebsessionManager mgr ^IVirtualBox vbox machine-id]
  (agent {:mgr mgr :vbox vbox :machine-id machine-id}))

(defn vbox-task [task-fn] ;; task-fn must take ISession as first parameter
  (fn [machine-agent]
    (let [^IVirtualBox vbox (:vbox machine-agent)
          ^IWebsessionManager mgr (:mgr machine-agent)
          machine-id (:machine-id machine-agent)
          ^ISession session (.getSessionObject mgr vbox)]
      (try
        (if (nil? session) (println "Couldn't create session!!!"))
        (println "opening session for machine-id:" machine-id)
        (.openSession vbox session machine-id)
        (task-fn session)
        (catch Exception e
          (println "ERROR" e))
        (finally ;; always make sure the session is closed!
         (println "closing session for machine-id" machine-id)
         (.close session))))
    machine-agent))

(comment
  "execute this at the command line to disable password checking
   $ VBoxManage setproperty websrvauthlibrary null
   execute this at the command line to start server
   $ vboxwebsrv -v"
  (def mgr (create-session-manager "localhost"))
  (def vbox (create-vbox mgr "test" "test"))
  (map print-vm (vm-list vbox)) ; print available images
  (start-vm mgr vbox "CentOS Test"))

(comment
  "Agent-based access to machines"
  (def mgr  (create-session-manager "localhost"))
  (def vbox (create-vbox mgr "test" "test"))
  (defn set-memory-task [n-megas]
    (fn [session]
      (let [^IMachine mutable-machine (.getMachine session)]
        (println "setting the memory to" n-megas "for machine-id" (.getId mutable-machine))
        (.setMemorySize mutable-machine (long n-megas))
        (.saveSettings mutable-machine))))
  (def my-centos-machine (machine mgr vbox "197c694b-fb56-43ed-88f5-f62769134442"))
  (send my-centos-machine (vbox-task (set-memory-task 1024))))

(comment
  "resetting agent"
  (restart-agent my-centos-machine
                 {:mgr mgr
                  :vbox vbox
                  :machine-id "197c694b-fb56-43ed-88f5-f62769134442" }
                 :clear-actions true))

(comment
  "TODO"
  "the vbox session times out and gets garbage collected after a few minutes of not being used.
     Find a way of catching this and re-loging on when this happens (transparently if possible)"
  "the agent should contain the last task's status for query")
