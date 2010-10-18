(ns vmfest.sandbox
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox
            ISession
            IMachine
            SessionState]))

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

(defn start-vm [^IWebsessionManager mgr ^IVirtualBox vbox vm-string]
  (let [^ISession session (.getSessionObject mgr vbox)
        machine (find-machine vbox vm-string)]
    (when machine
      (start-machine vbox machine session))
    (.close session))) ; todo: wrap the above in a try-catch an make
                       ; sure the session is always closed

(defn machine [^IWebsessionManager mgr ^IVirtualBox vbox machine-id]
  (agent {:mgr mgr :vbox vbox :machine-id machine-id}))

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
  "TODO"
  "the vbox session times out and gets garbage collected after a few minutes of not being used.
     Find a way of catching this and re-loging on when this happens (transparently if possible)"
  "There can only be one session open per VBox, not per machine!!!! but ha! If you create many
diferent VBox with the same credentials, each VBox can each one use one machine concurrently with the others, as long as it none of them opens the same one."
  "the agent should contain the last task's status for query")