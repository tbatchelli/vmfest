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

(defn get-vb-m
  "Gets the virtual machine corresponding to the supplied id. If such machine can't be found,
 it will raise a conditions."
  [vbox id]
  (try (.getMachine vbox id)
       (catch Exception e
         (conditions/log-and-raise e {:log-error :error
                                      :message (format "The machine with id='%s' is not found in %s."
                                                       id
                                                       (:url (:server vbox)))}))))

(defn find-vb-m
  ([vbox id-or-name]
      (try
        (.getMachine vbox id-or-name)
        (catch Exception e
          (try
            (.findMachine vbox id-or-name)
            (catch Exception e
              (log/warn (format "Machine identified by '%s' not found."
                                id-or-name))))))))

(defn get-machine
  "Will raise a condition if machine cannot be found."
  [url login password id]
  (let [server (Server. url login password)]
       (session/with-vbox server [mgr vbox]
         (when-let [vb-m (get-vb-m vbox id)]
           (model/dry vb-m server)))))

(defn find-machine [url login password id-or-name]
  (let [server (Server. url login password)]
       (session/with-vbox server [mgr vbox]
         (when-let [vb-m (find-vb-m vbox id-or-name)]
           (model/dry vb-m server)))))

;; I can't require this until find-machine is defined.
(require 'vmfest.virtualbox.machine)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (use '[vmfest.virtualbox.virtualbox :as vbox])

  ;; find by name or UUID
  (def my-machine (vbox/find-machine "http://localhost:18083" "" "" "CentOS Minimal"))
  ;; -> #:vmfest.virtualbox.model.machine{
  ;;           :id "197c694b-fb56-43ed-88f5-f62769134442",
  ;;           :server #:vmfest.virtualbox.model.server{
  ;;                      :username "",
  ;;                      :password ""},
  ;;           :location nil}

  ;; obtain all the attributes of machine
  (use 'vmfest.virtualbox.model)
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

  ;; operate the machine
  (use '[vmfest.virtualbox.machine :as machine])
  (machine/start my-machine)
  (machine/pause my-machine)
  (machine/resume my-machine)
  (machine/stop my-machine)

  ;; query the virtualbox for objects
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (vbox/machines server)

  ;; operate on machines
  (use '[vmfest.virtualbox.session :as session])

  ;; read-only
  (session/with-no-session my-machine [machine]
    (.getMemorySize machine))

  ;; read/write
  (session/with-direct-session my-machine [session machine]
    (.setMemorySize machine (long 1024))
    (.saveSettings machine)))