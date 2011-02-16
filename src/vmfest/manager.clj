(ns vmfest.manager
  (require [vmfest.virtualbox.virtualbox :as vbox]
           [vmfest.virtualbox.machine :as machine]
           [vmfest.virtualbox.session :as session]
           [vmfest.virtualbox.model :as model]
           [clojure.contrib.logging :as log]
           vmfest.virtualbox.medium)
  (use clojure.contrib.condition))

(defn server [url & [identity credentials]]
  (vmfest.virtualbox.model.Server. url (or identity "") (or credentials "")))

(def *locations*
  {:local {:model-path "/Users/tbatchlelli/.vmfest/models"
           :node-path "/Users/tbatchelli/.vmfest/nodes"}})

(def *location* (:local *locations*))

;; machine configuration stuff

(defn add-ide-controller [m]
  (machine/add-storage-controller m "IDE Controller" :ide))

(defn attach-device [m name controller-port device device-type uuid]
  (machine/attach-device m name controller-port device device-type uuid))

(defn set-bridged-network [m interface]
  (machine/set-network-adapter m 0 :bridged interface)
  (.saveSettings m))

(defn configure-machine [vb-m param-map]
  (machine/set-map vb-m param-map)
  (.saveSettings vb-m))

(defn basic-config [m]
  (let [parameters
        {:memory-size 512
         :cpu-count 1}]
    (configure-machine m parameters)
    (set-bridged-network m "en1: AirPort 2")
    (add-ide-controller m)))

(defn attach-hard-disk [vb-m image-uuid]
  (session/with-direct-session vb-m [_ m]
    (attach-device m "IDE Controller" 0 0 :hard-disk image-uuid)
    (.saveSettings m)))

(defn attach-image [vb-m uuid]
  (attach-hard-disk uuid)
  (throw (RuntimeException. "Image not found.")))

(defn get-ip [machine]
  (session/with-remote-session machine [_ console]
    (machine/get-guest-property console "/VirtualBox/GuestInfo/Net/0/V4/IP")))

(defn set-extra-data [machine key value]
  (session/with-direct-session machine [_ vb-m]
    (machine/set-extra-data vb-m key value)))

(defn get-extra-data [machine key]
  (log/info
   (format "get-extra-data: getting extra data for %s %s" (:id machine) key))
  (handler-case :type
    (log/info (str "get-extra-data: trying to get a no-session "))
    (session/with-no-session machine [vb-m]
      (machine/get-extra-data vb-m key))))

;;; jclouds/pallet-style infrastructure

(def *machine-models*
  {:micro basic-config})

(def *images*
  {:cent-os-5-5 {:description "CentOS 5.5 32bit"
                              :uuid "3a971213-0482-4eb8-8cfd-7eefc9e8b0fe"
                              :os-type-id "RedHat"}})

(defn create-machine [server name os-type-id config-fn image-uuid & [base-folder]]
  (let [m (session/with-vbox server [_ vbox]
            (let [machine (vbox/create-machine vbox name os-type-id (or base-folder (:node-path *location*)))]
              (config-fn machine)
              (machine/save-settings machine)
              (vbox/register-machine vbox machine)
              (vmfest.virtualbox.model.Machine. (.getId machine) server nil)))]
    ;; can't set the drive 
    (attach-hard-disk m image-uuid)
    m))

(defn instance [server name image-key machine-key & [base-folder]]
  (let [image (image-key *images*)
        config-fn (machine-key *machine-models*)]
    (when-not (and image config-fn)
      (throw (RuntimeException. "Image or Machine not found")))
    (let [uuid (:uuid image)
          os-type-id (:os-type-id image)]
      (create-machine server name os-type-id config-fn uuid base-folder))))

;;; machine control
(defn current-time-millis []
  (System/currentTimeMillis))

(defn wait-for-machine-state [m state-keys & [timeout-in-ms]]
  (let [begin-time (current-time-millis)
        timeout (or timeout-in-ms 1500)
        target-state? (fn [state-key] (some #(= state-key %) state-keys))]
    (loop []
      (let [current-state (session/with-no-session m [vb-m]
                            (machine/state vb-m))]
        (if (target-state? current-state)
          current-state
          (if (> (- (current-time-millis) begin-time) timeout)
            nil
            (do
              (Thread/sleep 1000)
              (recur))))))))

(defn state [^Machine m]
  (session/with-no-session m [vb-m] (machine/state vb-m)))

(defn start
  [^Machine m & opt-kv]
  (let [server (:server m)
        machine-id (:id m)]
    (session/with-vbox server [mgr vbox]
      (apply machine/start mgr vbox machine-id opt-kv))))

(defn stop
  [^Machine m]
  (session/with-remote-session m [_ console]
    (machine/stop console)))

(defn pause
  [^Machine m]
  (session/with-remote-session m [_ console]
    (machine/pause console)))

(defn resume
  [^Machine m]
  (session/with-remote-session m [_ console]
    (machine/resume console)))

(defn power-down
  [^Machine m]
  (handler-case :type 
    (session/with-remote-session m [_ console]
      (machine/power-down console))
    (handle :vbox-runtime
      (log/warn "Trying to stop an already stopped machine"))))

(defn destroy [machine]
  (let [vbox (:server machine)]
    (try
      (let [settings-file (:settings-file-path (model/as-map machine))]
        (let [progress (power-down machine)]
          (when progress
            (.waitForCompletion progress -1)))
        (session/with-direct-session machine [_ vb-m]
          (machine/remove-all-media vb-m))
        (session/with-vbox vbox [_ vbox]
          (vbox/unregister-machine vbox machine)) 
        (.delete (java.io.File. settings-file))))))



;;; virtualbox-wide functions

(defn hard-disks [server]
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getHardDisks vbox)))))

(defn machines [server]
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getMachines vbox)))))

(defn get-machine
  "Will raise a condition if machine cannot be found."
  [server id]
  (session/with-vbox server [mgr vbox]
    (when-let [vb-m (vbox/get-vb-m vbox id)]
      (model/dry vb-m server))))

(defn find-machine [server id-or-name]
  (session/with-vbox server [mgr vbox]
    (when-let [vb-m (vbox/find-vb-m vbox id-or-name)]
      (model/dry vb-m server))))

(defn guest-os-types [server] 
  (session/with-vbox server [mgr vbox]
    (let [get-keys (fn [os-type-map]
                     (select-keys os-type-map [:id :description :family-description :64-bit?]))]
      (map (comp get-keys vmfest.virtualbox.guest-os-type/map-from-IGuestOSType)
           (.getGuestOSTypes vbox)))))

;;; model forwards

(defn as-map [& params]
  (apply model/as-map params))

(comment "without pallet-style infrastructure"
  (use 'vmfest.manager)
  (def my-server (server "http://localhost:18083"))
  (guest-os-types my-server) ;; see the list of os guests
  ;; create and start one server with a configuring function 
  (def my-machine (create-machine my-server "my-name" "Linux" basic-config))
  (start my-machine)
  ;; create and start many servers
  (def clone-names #{"c1" "c2" "c3" "c4" "c5" "c6"})
  (def my-machines (map #(create-machine my-server % "Linux" basic-config) clone-names))
  (map start my-machines)
  (map stop my-machines))

(comment "with pallet-style infrastructure"
  (use 'vmfest.manager)
  ;; create a connection to the vbox server
  (def my-server (server "http://localhost:18083"))
  
  ;; instantiate a new machine based on a supplied hardware model and image
  (def my-machine (instance my-server "boot14" :cent-os-5-5 :micro))

  ;; operate the machine
  (start my-machine)
  (pause my-machine)
  (resume my-machine)
  (stop my-machine) ;; tells the OS to shut down
  (power-down my-machine) ;; turns off the machine
  ;; destroy the machine. All files will be deleted (irrecoverable)
  (destroy my-machine)

  ;; list available hard drives
  (hard-disks my-server)
  ;; view all info about the hard-drives
  (map as-map (hard-disks my-server))

  ;;view all available machines
  (machines my-server)
  ;;get a machine by name or id
  (def my-test-machine (find-machine my-server "CentOS Test")))

(comment "From pallet"
         "start swank with $ mvn -Pno-jclouds,vmfest clojure:swank"
         (require 'pallet.core)
         (def service (pallet.compute/compute-service-from-config-file "virtualbox"))
         (def my-node (pallet.core/make-node "pallet-test" {:image-id :cent-os-5-5 :packager :yum :os-family :centos}))
         (pallet.core/converge {my-node 1} :compute service)
         (pallet.core/converge {my-node 0} :compute service))