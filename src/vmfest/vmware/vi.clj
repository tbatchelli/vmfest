(ns vmfest.vmware.vi
  (:gen-class)
  (:use [clojure.pprint :as pprint])
  (:import [com.vmware.vim25.mo ServiceInstance InventoryNavigator]
           [java.net  URL]))

(defn sleep [seconds]
  (. java.lang.Thread sleep (* seconds 1000)))

(defn wait-for-task [task description]
  (let [state (.getState (.getTaskInfo task))]
    (cond (= state com.vmware.vim25.TaskInfoState/error)
          (do (println description " finished with an error") "error")

          (= state com.vmware.vim25.TaskInfoState/success)
          (do (println description " finished with success") "success")

          (= state com.vmware.vim25.TaskInfoState/queued)
          (do (println  "waiting for " description " : queued")
              (sleep 10)
              (recur task description))

          (= state com.vmware.vim25.TaskInfoState/running)
          (do (println "waiting for " description " : running "
                       (.getProgress (.getTaskInfo task)))
              (sleep 10)
              (recur task description)))))

(defn get-si [host-ip-or-dns user password]
  (ServiceInstance.
   (java.net.URL. (str "https://" host-ip-or-dns "/sdk"))
   user password true))

(def ^:dynamic *vc*
  {:connection nil
   :url nil
   :username nil
   :password nil})

(def vc-si (atom nil))
(defn vc-login [vc username password]
  (reset! vc-si (get-si vc username password)))

(defn get-connection 
  "if a VC connection exists for this thread use that,
   otherwise use the globally set connection"
  []
  (if-let [con (*vc* :connection)]
    con
    @vc-si))

(defmacro with-vc-login
  "Logs into a VC, runs the opperations in the body and then logs out"
  [{vc :vc username :username password :password} & body]
  (let [vc# vc
        username# username
        password# password]
    `(binding [*vc* {:connection (get-si ~vc# ~username# ~password#)
                   :url ~vc#
                   :username ~username#
                     :password ~password#}]
       (try
         ~@body
         (finally
          (-> (*vc* :connection) .getServerConnection .logout))))))

(defn get-vms []
  (let [vc-inv (new InventoryNavigator (. (get-connection) getRootFolder))]
    (. vc-inv searchManagedEntities "VirtualMachine")))

(defn get-dcs []
  (let [vc-inv (new InventoryNavigator (. (get-connection) getRootFolder))]
    (. vc-inv searchManagedEntities "Datacenter")))

(defn get-hosts []
  (let [vc-inv (new InventoryNavigator (. (get-connection) getRootFolder))]
    (. vc-inv searchManagedEntities "HostSystem")))

(defn get-vm-by-name [name]
  (first (filter #(= (. % getName) name) (get-vms))))

(defn get-host-by-name [name]
  (first (filter #(= (. % getName) name) (get-hosts))))

(defn get-datastores-by-host-and-name [host-name datastore-name]
  (filter #(= datastore-name (.getName %))
          (.getDatastores (get-host-by-name host-name))))

(defn get-network-by-host-and-name [host-name net-name]
  (first (filter #(= (.getName %) net-name)
                 (.getNetworks (get-host-by-name host-name )))))

(defn vms-in-testbed [search-regex]
  (filter #(not-empty (re-find search-regex
                               (.getName %)))
          (get-vms)))

(defn get-datastores [vm]
  (map #(.getName (.getSummary %)) (.getDatastores vm )))

(defn suspend-vm [vm-name]
  (wait-for-task (.suspendVM_Task (get-vm-by-name vm-name)) (str "suspending " vm-name)))

(defn get-vms-on-datastore [datastore-names-set]
  (filter (fn [vm] (some #(datastore-names-set %)
                             (get-datastores vm)))
               (get-vms)))

(defn snapshot-vm [vm-name snapshot-name]
  (.waitForMe
   (.createSnapshot_Task (get-vm-by-name vm-name)
                         snapshot-name snapshot-name false false)))
(defn get-snapshot [vm-name snapshot-name]
  (first (filter #(= (.getDescription %) snapshot-name)
                 (.rootSnapshotList (.getSnapshot (get-vm-by-name vm-name))))))

(defn revert-to-current-snapshot [vm-name host-name]
  (.waitForMe (.revertToCurrentSnapshot_Task
               (get-vm-by-name vm-name) (get-host-by-name host-name))))

(defn delete-snapshot)

(defn delete [vm-to-delete]
  (println "deleting: " (.getName vm-to-delete))
  (try (wait-for-task (.. vm-to-delete powerOffVM_Task)
                      (str "power off " (.getName vm-to-delete))) ; turn off
       (catch com.vmware.vim25.InvalidPowerState _))   ; already-off?
  (try (wait-for-task (.. vm-to-delete destroy_Task)
                      (str "delete " (.getName vm-to-delete))) ; delete files
       (catch com.vmware.vim25.FileNotFound _)))   ; already-deleated?

(defn power-on [vm-to-start host]
  (println "starting: " (.getName vm-to-start))
  (try (wait-for-task (.. vm-to-start (powerOnVM_Task host))
                      (str "starting " (.getName vm-to-start))) ; turn on
       (catch com.vmware.vim25.InvalidPowerState _))) ; already-on?

(defn power-on-by-name [vm-name host-name]
   (power-on (get-vm-by-name vm-name) (get-host-by-name host-name)))

(defn delete-by-name [name]
  (let [vm-to-delete (get-vm-by-name name)]
    (if (nil? vm-to-delete)
      (println "no vm " name "exists to be deleted")
      (delete vm-to-delete))))

(defn delete-testbed [pattern]
  (println "deleting testbed " number)
  (doall (pmap delete (vms-in-testbed pattern))))

(defn start-testbed [pattern host]
  (doall (pmap #(power-on % (get-host-by-name host)) (vms-in-testbed pattern))))

(defn powered-off [vms]
  (doall (filter #(= (.. % getRuntime powerState)
                          com.vmware.vim25.VirtualMachinePowerState/poweredOff)
                      vms)))

(defn powered-on [vms]
  (doall (filter #(= (.. % getRuntime powerState)
                          com.vmware.vim25.VirtualMachinePowerState/poweredOn)
                      vms)))
(defn names [vms] (map #(.getName %) vms))

(defn get-ip [vm]
  (.. vm getGuest getIpAddress))

(defn get-ips [vms]
  (map get-ip vms))

(defn get-names-and-ips []
  (-> (get-vms)
      powered-on
      (#(zipmap (names %)(get-ips %)))))

(defn get-devices [vm-name]
  (.. (get-vm-by-name vm-name) getConfig getHardware getDevice))

(defn get-device-by-name [vm-name dev-name]
  (first (filter #(= (.getLabel (.deviceInfo %)) dev-name) (get-devices vm-name))))

(defn make-net-dev [host-name vm-name net-name device-name]
  (let [network (get-network-by-host-and-name host-name net-name)
        nic-backing-info (com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo.)
        dev-connect-info (com.vmware.vim25.VirtualDeviceConnectInfo.)
        nic (com.vmware.vim25.VirtualE1000.)
        nic-dev-spec (com.vmware.vim25.VirtualDeviceConfigSpec.)
        original-dev (get-device-by-name vm-name device-name)] 
    (if (= (type network) com.vmware.vim25.mo.DistributedVirtualPortgroup)
      (.setDeviceName nic-backing-info (.getKey (.getConfig network))) ;workaround for VDSwitches
      (.setDeviceName nic-backing-info net-name))
    (.setNetwork nic-backing-info (.getMOR network))
    (doto dev-connect-info
      (.setAllowGuestControl true)
      (.setConnected true)
      (.setStartConnected true))
    (doto nic
      (.setBacking nic-backing-info)
      (.setKey (.getKey original-dev))
      (.setUnitNumber (.getUnitNumber original-dev))
      (.setAddressType "generated")
      (.setConnectable dev-connect-info))
    (doto nic-dev-spec
      (.setDevice nic)
      (.setOperation com.vmware.vim25.VirtualDeviceConfigSpecOperation/edit))
    nic-dev-spec))

(defn clone-vm [old-name new-name host-name data-store-name net-names]
  (let [vm (get-vm-by-name old-name)
        destination-host (get-host-by-name  host-name)
        datastore (first (get-datastores-by-host-and-name host-name data-store-name))
        pool (.. destination-host getParent getResourcePool)
        folder (.getParent vm)
        relocate-spec (com.vmware.vim25.VirtualMachineRelocateSpec.)
        config-spec (com.vmware.vim25.VirtualMachineConfigSpec.)
        clone-spec (com.vmware.vim25.VirtualMachineCloneSpec.)]
    (doto relocate-spec
      (.setDatastore (.getMOR datastore))
      (.setPool (.getMOR pool))
      ;(.setDiskMoveType "moveChildMostDiskBacking")
      (.setDiskMoveType "moveAllDiskBackingsAndAllowSharing")
      (.setHost (.getMOR destination-host)))
    (doto config-spec
      (.setName new-name)
      (.setMemoryMB (Long. 2048))
      (.setNumCPUs (Integer. 2))
      (.setDeviceChange 
       (into-array com.vmware.vim25.VirtualDeviceConfigSpec
                   (map (fn [[dev net]]
                          (make-net-dev host-name old-name net dev))
                        net-names))))
    (doto clone-spec
      (.setPowerOn false)
      (.setTemplate false)
      (.setLocation relocate-spec)
      (.setConfig config-spec))
    (let [task (.cloneVM_Task vm folder new-name clone-spec)]
      (wait-for-task task (str "cloning " new-name)))))

(defn plug-in-network [vm-name device-name]
  (let [config-spec (com.vmware.vim25.VirtualMachineConfigSpec.)
        nic (get-device-by-name vm-name device-name)
        nic-dev-spec (com.vmware.vim25.VirtualDeviceConfigSpec.)]
    (.setConnected (.getConnectable nic) false)
    (doto nic-dev-spec
      (.setDevice nic)
      (.setOperation com.vmware.vim25.VirtualDeviceConfigSpecOperation/edit))
    (.setDeviceChange config-spec (into-array com.vmware.vim25.VirtualDeviceConfigSpec [nic-dev-spec]))
    (.waitForMe (.reconfigVM_Task (get-vm-by-name vm-name) config-spec))))

(def power-states
  {com.vmware.vim25.VirtualMachinePowerState/poweredOn :poweredOn
   :poweredOn com.vmware.vim25.VirtualMachinePowerState/poweredOn
   com.vmware.vim25.VirtualMachinePowerState/poweredOff :poweredOff
   :poweredOff com.vmware.vim25.VirtualMachinePowerState/poweredOff
   com.vmware.vim25.VirtualMachinePowerState/suspended :suspended
   :suspended com.vmware.vim25.VirtualMachinePowerState/suspended})

(defn report
  ([] (report (range 1 41)))
  ([testbed-numbers]
     (zipmap (map #(str "testbed" %) testbed-numbers)
              (map #(map (fn [vm]
                           {:name (.getName vm)
                            :datastores (get-datastores vm)
                            :IPs (get-ips [vm])
                            :power (power-states (.. vm getRuntime powerState))
                            :active-memory (.. vm getSummary quickStats guestMemoryUsage)
                            :private-memory (.. vm getSummary quickStats privateMemory)
                            :swapped-memory (.. vm getSummary quickStats swappedMemory)
                            :ballooned-memory (.. vm getSummary quickStats getBalloonedMemory)})
                         (vms-in-testbed %))
                   testbed-numbers))))

(defn status
  "takes an optional collection of testbed number ie: (status [1 5 7]
to print a report on these three testbeds, or just (status) for all testbeds"
  ([] (status (range 1 41)))
  ([testbed-numbers]
     (let [report (report testbed-numbers)
           stats (map #(let [name (key %)
                             names (map :name (val %))
                             power-states (map :power (val %))
                             active-memory (map :active-memory (val %))
                             private-memory (map :private-memory (val %))
                             swapped-memory (map :swapped-memory (val %))
                             ballooned-memory (map :ballooned-memory (val %))
                             powerfn (fn [state]
                                       (filter (fn [power-state] (= state power-state))
                                               power-states))]
                         {:testbed name
                          :vms (count names)
                          :powered-on (count (powerfn :poweredOn))
                          :powered-off (count (powerfn :poweredOff))
                          :suspended (count (powerfn :suspended))
                          :active-memory (reduce + (filter (comp not nil?) active-memory))
                          :private-memory (reduce + (filter (comp not nil?) private-memory))
                          :swapped-memory (reduce + (filter (comp not nil?) swapped-memory))
                          :ballooned-memory (reduce + (filter (comp not nil?) ballooned-memory))})
                      report)
           sumfn (fn [key] (reduce + (filter (comp not nil?) (map key stats))))
           totals {:vms            (sumfn :vms)
                   :powered-on     (sumfn :powered-on)
                   :powered-off    (sumfn :powered-off)
                   :suspended      (sumfn :suspended)
                   :active-memory  (sumfn :active-memory)
                   :private-memory (sumfn :private-memory)
                   :swapped-memory (sumfn :swapped-memory)
                   :ballooned-memory (sumfn :ballooned-memory)}]
       {:totals totals
        :stats stats
        :report report})))

(defn print-status [status]
  "prints the result of calling status"
  (println "Totals:")
  (pprint (status :totals))
  (println "\nSummary")
  (pprint (status :stats))
  (println "\n\nTestbed Details")
  (pprint  (status :report)))


(def critical 2)
(def warning  1)
(def healthy  0)

(defn hosts-running [status]
  (if (= ((status :totals) :vms)
         (+ ((status :totals) :powered-off)
            ((status :totals) :suspended)))
    (do (println "no hosts powered on") critical)
    healthy))

(defn swapped-memory [status]
  (let [totals (status :totals)
        swapped-memory (totals :swapped-memory)]
    (if (> swapped-memory 0)
      (do (println "Memory Swapping: " swapped-memory "MB." ) critical)
      healthy)))

(defn ballooned-memory [status]
  (let [totals (status :totals)
        ballooned-memory (totals :ballooned-memory)]
    (if (> ballooned-memory 100)
      (do (println "Ballooned Memory " ballooned-memory) warning)
      healthy)))

(def tests [hosts-running swapped-memory ballooned-memory])
(defn analyze-status [status]
  (let [result (reduce max (map #(% status) tests))]
    (if (zero? result)
      (println (-> status :totals :vms) " active VMS"))
    result))


(comment

  (with-vc-login
        {:vc    "my.vc.company.com"
         :username "administrator"
         :password "sUpers3kret"}
        (with-vc-login
          (clone-vm "template-vm-name"
                    "new-vm-name"
                    "name-of-host-on-vcenter"
                    "my-datastore-name"
                    [["eth0" "Network1"] ["eth1" "Network2"]]))))