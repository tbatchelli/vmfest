(ns vmfest.virtualbox.virtualbox
  (:require [clojure.contrib.logging :as log]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.session :as session]
            vmfest.virtualbox.guest-os-type) 
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox]
           [vmfest.virtualbox.model
            Server
            Machine]))

(defn hard-disks [server]
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getHardDisks vbox)))))

(defn machines [server]
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getMachines vbox)))))

(defn find-vb-m
  ([vbox id-or-name]
      (try
        (.getMachine vbox id-or-name)
        (catch Exception e
          (try
            (.findMachine vbox id-or-name)
            (catch Exception e
              (conditions/log-and-raise e :warn (format "There is no machine identified by '%s'" id-or-name)
                                           :object-not-found)))))))

(defn find-machine [url login password id-or-name]
  (let [server (Server. url login password)]
       (session/with-vbox server [mgr vbox]
         (when-let [vb-m (find-vb-m vbox id-or-name)]
           (model/dry vb-m server)))))

;; I can't require this until find-machine is defined.
(require 'vmfest.virtualbox.machine)

(comment
  ;;----------------------------------------------------------
  ;; NOTICE: this is mainly deprecated. see vmfest/vmfest.clj
  ;;----------------------------------------------------------
  
  (use 'vmfest.virtualbox.virtualbox)
  
  ;; this is only necessary to obtain the machine-id for the target virtual machine
  (def mgr (create-session-manager "http://localhost:18083"))
  (def vbox (create-vbox mgr "" ""))
  (def machine-id (.getId (find-machine vbox "CentOS Minimal")))

  ;; this is the only code actually
  ;; needed to configure the virtual
;; machine
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (def vb-m (vmfest.virtualbox.model.Machine. machine-id server nil))
  
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


(comment
  ;;----------------------------------------------------------
  ;; NOTICE, this is mainly deprecated. see vmfest/vmfest.clj
  ;;----------------------------------------------------------
  
  (use 'vmfest.virtualbox.virtualbox)
  (use 'vmfest.virtualbox.machine)
  (use 'vmfest.virtualbox.guest-os-type)

  ;; find by name or UUID
  (def my-machine  (find-machine "http://localhost:18083" "" "" "CentOS Minimal"))
  ;; -> #:vmfest.virtualbox.model.machine{
  ;;           :id "197c694b-fb56-43ed-88f5-f62769134442",
  ;;           :server #:vmfest.virtualbox.model.server{
  ;;                      :username "",
  ;;                      :password ""},
  ;;           :location nil}

  ;; operate the machine
  (start my-machine)
  (pause my-machine)
  (resume my-machine)
  (stop my-machine)

  ;; all the statements in the body are run within a direct session
  ;; with the machine
  (with-direct-session my-machine [session machine]
    (.setMemorySize machine (long 1024))
    (.saveSettings machine))
  
  ;; as-map gives you a map with all the object's attributes
  (pprint (as-map my-machine))
  ;; -> {:id "197c694b-fb56-43ed-88f5-f62769134442",
  ;;     :server {:url "http://localhost:18083", :username "", :password ""},
  ;;     :location nil,
  ;;     :current-snapshot nil,
  ;;     :cpu-hot-plug-enabled? false,
  ;;     :settings-file-path
  ;;             "/Users/tbatchelli/Library/VirtualBox/Machines/CentOS Minimal/CentOS Minimal.xml",
  ;;     :hpet-enabled false,
  ;;     :teleporter-port 0,
  ;;     :cpu-count 1,
  ;;     :snapshot-folder
  ;;             "/Users/tbatchelli/Library/VirtualBox/Machines/CentOS
  ;;             Minimal/Snapshots",
  ;; etc.... }

  ;; you can also obtain attributes from the machine one by one
  (with-no-session my-machine [machine]
    (.getMemorySize machine))
  )

(comment
  (use '[vmfest.virtualbox.virtualbox :as vbox])
  (def my-machine (vbox/find-machine "http://localhost:18083" "" "" "CentOS Minimal"))
  (use 'vmfest.virtualbox.model)
  (as-map my-machine)
  (use '[vmfest.virtualbox.machine :as machine])
  (machine/start my-machine)
  (machine/stop my-machine)
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (vbox/machines server)
  (use '[vmfest.virtualbox.session :as session])
  (session/with-no-session my-machine [machine]
    (.getMemorySize machine))
  (session/with-direct-session my-machine [session machine]
    (.setMemorySize machine (long 1024))
    (.saveSettings machine)))