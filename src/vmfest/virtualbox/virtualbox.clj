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

(defn hard-disks*
  "Gets all information fields from all the registered hard drives"
  [server]
  
  (session/with-vbox server [_ vbox]
    (doall (map #(model/as-map (model/dry % server)) (.getHardDisks vbox)))))

(defn machines [server]
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getMachines vbox)))))

(defn get-vb-m
  "Gets the virtual machine corresponding to the supplied id. If such machine can't be found,
 it will raise a conditions."
  [vbox id]
  (try (.getMachine vbox id)
       (catch Exception e
         (conditions/log-and-raise
          e
          {:log-error :error
           :message (format "The machine with id='%s' is not found in %s."
                            id
                            (:url (:server vbox)))}))))

(defn get-hard-disk
  [vbox id]
  (try (.getHardDisk vbox id)
       (catch Exception e
         (conditions/log-and-raise
          e
          {:log-error :error
           :message (format "The hard disk with id='%s' is not found in %s."
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

(defn find-hard-disk
  [vbox id-or-location]
  (try
    (.getHardDisk vbox id-or-location)
    (catch Exception e
      (try (.findHardDisk vbox id-or-location)
           (catch Exception e
             (log/warn (format "Can't find a hard disk located in '%s'."
                               id-or-location)))))))

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

(defn guest-os-types [server] 
  (session/with-vbox server [mgr vbox]
    (let [get-keys (fn [os-type-map]
                     (select-keys os-type-map [:id :description :family-description :64-bit?]))]
      (map (comp get-keys vmfest.virtualbox.guest-os-type/map-from-IGuestOSType)
           (.getGuestOSTypes vbox)))))

(defn register-machine [vbox machine]
  (try
    (.registerMachine vbox machine)
    (catch javax.xml.ws.WebServiceException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_OBJECT_NOT_FOUND
        {:message "No matching virtual machine found"}
        :VBOX_E_INVALID_OBJECT_STATE
        {:message "Virtual machine was not created within this VirtualBox instance."}}))))

(defn create-machine [vbox name os-type-id & [base-folder]]
  (try
    (.createMachine vbox name os-type-id (or base-folder "") nil false)
    (catch javax.xml.ws.WebServiceException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_OBJECT_NOT_FOUND {:message "invalid os type ID."}
        :VBOX_E_FILE_ERROR {:message "Resulting settings file name is invalid or the settings file already exists or could not be created due to an I/O error."}
        :E_INVALIDARG {:message "name is empty or null."}}))))

(defn unregister-machine [server machine]
  (try
    (session/with-vbox server [_ vbox]
      (let [uuid (:id machine)]
        (.unregisterMachine vbox uuid)))
    (catch javax.xml.ws.WebServiceException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_OBJECT_NOT_FOUND
        {:message "Could not find registered machine matching id."}
        :VBOX_E_INVALID_VM_STATE
        {:message "Machine is in Saved state."}
        :VBOX_E_INVALID_OBJECT_STATE
        {:message "Machine has snapshot or open session or medium attached."}}))))

(defn delete-machine [machine]
  (session/with-direct-session machine [s m]
    (try 
      (.deleteSettings (.getMachine s))
      (catch javax.xml.ws.WebServiceException e
        (conditions/wrap-vbox-runtime
         e
         {:VBOX_E_INVALID_VM_STATE
          {:message "Cannot delete settings of a registered machine or machine not mutable."}
          :VBOX_E_IPRT_ERROR
          {:message "Could not delete the settings file."}})))))


;; I can't require this until find-machine is defined.
(require '[ vmfest.virtualbox.machine :as machine])




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