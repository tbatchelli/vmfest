(ns vmfest.manager
  "High level API for VMFest. If you don't want to do anything too fancy, start here.

   Manager provides a cloud-like interface to vmfest. You can set it
up with a number of model images that later can be used to create
ephemeral machines that will boot from those model images. Many
machines, all based on those models. An ephemeral machine will see its
boot HD reset when it is stopped, so they're always guaranteed to
reboot to a fresh state. Also, destroying a machine will remove any
trace of it ever having existed.

By default, image models are stored in ~/.vmfest/models and the instantiated
machines are stored in ~/.vmfest/nodes ."
  (:require [vmfest.virtualbox.virtualbox :as vbox]
            [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.machine-config :as machine-config]
            [vmfest.virtualbox.session :as session]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.image :as image]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [fs.core :as fs]
            vmfest.virtualbox.medium
            clojure.set)
  (:use [slingshot.slingshot :only [throw+ try+]]
        [vmfest.virtualbox.version :only (xpcom?)])
  (:import [org.virtualbox_4_2
            SessionState
            HostNetworkInterfaceType
            HostNetworkInterfaceStatus
            IHostNetworkInterface
            IVirtualBox]
           java.io.File
           vmfest.virtualbox.model.Machine)
  (:import [java.net NetworkInterface InetAddress]))

(def supported-api-version "4_2")

(defn check-vbox-api-version
  "Checks whether the underlying VirtualBox system provides an API
  version supported by this version of VMFEST. True if it does, false
  otherwise"
  [server]
  (session/with-vbox server [_ vbox]
    (= (vbox/api-version vbox) supported-api-version)))

(defn server
  "Builds a connection definition to the VM Host and checks that the
  underlying system provides a supported API version

  (server) will default to the url \"http://localhost:18083\""
  [& [url identity credentials]]
  (let [url (or url "http://localhost:18083")
        server
        (vmfest.virtualbox.model.Server. url (or identity "") (or credentials ""))]
    (when-not (check-vbox-api-version server)
      (throw+ {:type :unsupported-vbox-api-version
               :message
               (format
                "This version of VMFest only supports VirtualBox API v. %s "
                supported-api-version )}))
    server))

(def user-home (System/getProperty "user.home"))

(defn default-model-path
  "Return the default model-path for images"
  [& {:keys [home] :or {home user-home}}]
  (.getPath (io/file home ".vmfest" "models")))

(defn default-node-path
  "Return the default node-path for images"
  [& {:keys [home] :or {home user-home}}]
  (.getPath (io/file home ".vmfest" "nodes")))

;; In the future, vmfest will be able to handle more than just one VM
;; host. *locations* will hold the locations. For now, it only holds
;; one location: local.
(def ^{:dynamic true} *locations*
  {:local {:model-path (default-model-path)
           :node-path (default-node-path)}})

;; current location. See *locations*
(def ^{:dynamic true} *location* (:local *locations*))


(defn get-machine-attribute [machine key]
  (session/with-no-session machine [m]
    (machine/get-attribute m key)))

(defn get-ip
  "Gets the IP Address of network card in the slot in the position
  specified by 'slot' (0 indexed). If no slot is specified, it will
  use 0.
"
  [machine & {:keys [slot] :or {slot 0}}]
  {:pre [(model/Machine? machine)]}
  (log/tracef "Getting IP address slot %s in machine \"%s\""
          slot (get-machine-attribute machine :name))
  (try
    (session/with-session machine :shared [session _]
      (machine/get-guest-property
       (.getConsole session)
       (format
        "/VirtualBox/GuestInfo/Net/%s/V4/IP"
        slot)))
    (catch org.virtualbox_4_2.VBoxException e
      (throw (RuntimeException. e)))))

(defn set-extra-data
  "Adds metadata to a machine, in the form of a key, value pair"
  [machine key value]
  {:pre [(model/Machine? machine)]}
  (session/with-session machine :shared [_ vb-m]
    (machine/set-extra-data vb-m key value)))

(defn get-extra-data [machine key]
  "Gets metadata from a machine by its key"
  {:pre [(model/Machine? machine)]}
  (log/tracef "get-extra-data: getting extra data for %s %s" (:id machine) key)
  (session/with-no-session machine [vb-m]
    (machine/get-extra-data vb-m key)))

(defn get-extra-data-keys [machine]
  "Gets metadata keys from a machine"
  {:pre [(model/Machine? machine)]}
  (log/tracef "get-extra-data-keys : getting extra data for %s" (:id machine))
  (session/with-no-session machine [vb-m]
    (machine/get-extra-data-keys vb-m)))



;;; host functions
(defn get-network-interfaces
  "Returns a map for each active host network interfaces."
  ([machine filter-map]
     (session/with-session machine :shared [_ server]
       (machine/get-network-interfaces server filter-map)))
  ([machine] (get-network-interfaces machine {:status :up})))

(defn get-network-adapters
  "Returns a map for each active host network interfaces."
  ([machine filter-map]
     (session/with-session machine :shared [_ server]
       (machine/get-network-adapters server filter-map)))
  ([machine] (get-network-adapters machine {})))

(defn get-usable-network-interfaces-from-vbox
  "Finds what host network interfaces are usable for bridging
  according to VirtualBox. It excludes the ones that are either not of
  type Bridged or they are not up. Returns a sequence of interfaces as
  maps with:
  [:name :os-name :ip-address :status :interface-type :medium-type].

Note: :os-name is how vbox identifies the host network interfaces."
  [server]
  (session/with-vbox server [_ vbox]
    (let [os-interface-name
          ;; needed because of how OSX adds a name to the OS i/f name
          ;; e.g. "en1: Airport" vs. "en1"
          (fn [^String name]
            (let [index  (.indexOf name ":")]
              (if (> index 0)
                (.substring name 0 index)
                name)))
          all-interfaces
          (map (fn [^IHostNetworkInterface ni]
                 {:name (.getName ni) ;; the full OSX name
                  :os-name (os-interface-name (.getName ni))
                  :ip-address (.getIPAddress ni)
                  :status (.getStatus ni)
                  :interface-type (.getInterfaceType ni)
                  :medium-type (.getMediumType ni)})
               (.getNetworkInterfaces (.getHost ^IVirtualBox vbox)))
          usable? ;; determine if an I/F is usable
          (fn [if]
            (and
             (= (:interface-type if) HostNetworkInterfaceType/Bridged)
             (= (:status if) HostNetworkInterfaceStatus/Up)))]
      (doall (filter usable? all-interfaces)) ;; force execution
      )))

(defn get-usable-network-interfaces-from-java
  "Finds what host network interfaces are usable for bridging according
to the JVM. This excludes interfaces that have no reachable ip addresses,
or that are either loopback, virtual or point-to-point.

Returns a sequence of interfaces as maps containing:
  [:name :ip-addresses :virtual? :loopback? :point-to-point? :up?]
  where :ip-addresses is a sequence of maps with
  [:ip-address :reachable?]." []
  (let [any-reachable? (fn [ips]
                         (some true? (map :reachable? ips)))
        nis (enumeration-seq (NetworkInterface/getNetworkInterfaces))
        ni-infos
        (map (fn [^NetworkInterface ni]
               (let [ip-addresses (enumeration-seq (.getInetAddresses ni))
                     ip-info (map
                              (fn [^java.net.InetAddress ip]
                                {:ip-address ip
                                 :reachable? (.isReachable ip 1000)})
                              ip-addresses)]
                 {:name (.getName ni)
                  :ip-addresses ip-info
                  :virtual? (.isVirtual ni)
                  :loopback? (.isLoopback ni)
                  :point-to-point? (.isPointToPoint ni)
                  :up? (.isUp ni)}))
             nis)
        usable?
        (fn [if]
          (and (not (or (:virtual? if)
                        (:loopback? if)
                        (:point-to-point? if)
                        (not (:up? if))))
               (any-reachable? (:ip-addresses if))))]
    (filter usable? ni-infos)))

(defn find-usable-network-interface
  "Provides a list of interface names that are usable for bridging in
VirtualBox"
  [server]
  (let [vbox-ifs(get-usable-network-interfaces-from-vbox server)
        usable-by-vbox ;; only their names
        (into #{} (map :os-name vbox-ifs))
        usable-by-java ;; only their names
        (into #{}
              (map :name (get-usable-network-interfaces-from-java)))
        usable-by-both ;; only their names
        (clojure.set/intersection usable-by-vbox usable-by-java)
        find-interface-by-os-name (fn [ifs os-name]
                  (first (filter #(= (:os-name %) os-name) ifs)))]
    (map #(:name (find-interface-by-os-name vbox-ifs %)) usable-by-both)))

;;; jclouds/pallet-style infrastructure

;; These are the hardware models to be instantiated.
(def ^{:dynamic true} *machine-models*
  {:micro
   {:memory-size 512
    :cpu-count 1
    :network [{:attachment-type :host-only
               :host-only-interface "vboxnet0"}
              {:attachment-type :nat}]
    :storage [{:name "IDE Controller"
               :bus :ide
               :devices [nil nil {:device-type :dvd} nil]}]
    :boot-mount-point ["IDE Controller" 0]}})

(def ^{:dynamic true} *images* nil)

(defn fs-dir [^String path]
  (seq (.listFiles (java.io.File. path))))

(defn image-meta-file? [^java.io.File file]
  (.. (.getName file) (endsWith ".meta")))

(defn read-meta-file [^java.io.File file]
  (try
    (let [meta (read-string (slurp file))
          uuid (:uuid (second (first meta)))]
      ;; ignore metas for which the image file is not present
      (if (fs/file? uuid)
        meta
        (do
          (log/warnf "Cannot find the image file '%s' referenced in '%s'. Skipping"
                      uuid (.getCanonicalPath file)) {})))
    (catch Exception e
      (log/warn (format "Wrong file format %s. Skipping" (.getName file)))
      {})))

(defn load-models
  "Return a map of all image metadata from the model-path"
  [& {:keys [model-path] :or {model-path (:model-path *location*)}}]
  (let [meta-files (filter image-meta-file? (fs-dir model-path))]
    (reduce merge {} (map read-meta-file meta-files))))

(defn update-models
  "Update model metadata in *images*"
  [& {:keys [model-path] :or {model-path (:model-path *location*)}}]
  (alter-var-root #'*images* (fn [_] (load-models :model-path model-path))))

(defn models
  [& {:keys [model-path] :or {model-path (:model-path *location*)}}]
  (keys (update-models :model-path model-path)))

(defn model-info
  [model-key & {:keys [model-path] :or {model-path (:model-path *location*)}}]
  (model-key (update-models :model-path model-path)))

(defn check-model
  [server model-key & {:keys [model-path]
                       :or {model-path (:model-path *location*)}}]
  (let [model-id (:uuid (model-key (update-models :model-path model-path)))]
    (session/with-vbox server [_ vbox]
      (image/valid-model? vbox model-id))))

;; force an model DB update
;; (update-models)

(defn mount-boot-image
  "For a machine configuration with an entry :boot-mount-point
  containing a vector [<storage bus name> <device id>], it will
  configure the machine so that the supplied HardDisk image is mounted
  in said bus and device id. Returns an updated machine configuration map"
  [config image-uuid]
  ;; TODO: This needs unit testing!
  (if-let [[storage-bus-name device-id] (:boot-mount-point config)]
    (let [boot-disk-config  {:device-type :hard-disk
                             :location image-uuid
                             :attachment-type :multi-attach}
          image-mounter (fn [{:keys [name] :as storage-bus}]
                          ;; When the storage bus is named with
                          ;; storage-bus-name, mount the image in the
                          ;; right device
                          (if (= name storage-bus-name)
                            ;; this is the controller: add image to devices
                            (update-in storage-bus
                                       [:devices]
                                       #(assoc %1 device-id boot-disk-config))
                            ;; not the right controller. Leave it as is.
                            storage-bus))]
      (update-in config [:storage] #(map image-mounter %1)))
    (do
      (log/warnf "Image not mounted. No mount point found for %s." config)
      config)))

(defn- ensure-image-is-registered [vbox location]
  "If the medium in the location is not open, it will open it as multi-attach"
  (let [medium
        (vbox/find-medium vbox location :hard-disk)]
    ;; if the image is not registered, register it
    (when-not medium
      (log/debugf
       (str "ensure-image-is-registered: %s not registered. Will "
            "attempt at registering if it is a hard drive")
       location)
      (let [medium (vbox/open-medium vbox location :hard-disk :read-only false)]
        (log/debugf
         "ensure-image-is-registered: hard disk %s successfully registered."
         location)
        (.close medium)))
    ;; ensure the image is not immutable, make it immutable
    (let [medium (vbox/find-medium vbox location :hard-disk)]
      (when-not (image/mutable? medium)
        (log/warnf "ensure-image-is-registered: %s is not mutable." location)
        (log/debugf
         "ensure-image-is-registered: attempting to make %s immutable."
         location)
        (image/make-immutable medium)))
    ;; Check that the image is successfully registered and immutable
    (image/valid-model? vbox location)))

(defn create-machine
  [server name os-type-id config image-uuid & [base-folder]]
  {:pre [(model/Server? server)]}
  (when-let [f (or base-folder (:node-path *location*))]
    (when-not (.exists (io/file f))
      (log/warnf
       "The path for saving nodes %s does not exist. Trying to create it." f)
      (try+
       (.mkdirs (io/file f))
       (log/infof "Created path for saving nodes: %s" f)
       (catch Exception e
         (throw+
          {:type :path-not-found
           :message
           (format
            "Path for saving nodes does not exist and could not be created: %s"
            f)})))))
  (let [m (session/with-vbox server [_ vbox]
            (let [machine (vbox/create-machine
                           vbox
                           name
                           os-type-id
                           true ;; overwrite whatever previous
                           ;; definition was there
                           (or base-folder (:node-path *location*)))]
              (machine-config/configure-machine machine config)
              (machine/save-settings machine)
              (vbox/register-machine vbox machine)
              (vmfest.virtualbox.model.Machine. (.getId machine) server nil)))]
    ;; Configure the storage buses in the machine
    (let [boot-image-mounted-config ;; first mount the image if needed
          (mount-boot-image config image-uuid)]
      (log/debugf "Configuring storage for %s" boot-image-mounted-config)
      (session/with-vbox server [_ vbox]
        (session/with-session m :shared [_ vb-m]
          ;; sometimes VBox seems to unregister images. We will make
          ;; sure the boot image is always registered before we try to
          ;; build the machine.
          (when image-uuid
            (ensure-image-is-registered vbox image-uuid))
          (machine-config/configure-machine-storage
           vb-m boot-image-mounted-config)
          (machine/save-settings vb-m))))
    m))

(defn new-image
  "Creates a new hard disk image as described by the `image-spec` map
  that contains the keys :size :format :variants :location. Only :size
  and :location are mandatory.

  vbox: an IVirtualBox

  image spec:
    :location File path where the image will be created
    :format the format of the image. One in
       (system-properties/supported-medium-formats)
    :size Logical size, in MB
    :variants a sequence with zero or more of the variants in
      (enums/medium-variant-type-to-key-table)

  e.g.:

   (new-image (server \"http://localhost:18083\"
               {:size 1024
                :location \"/tmp/my-image.vdi\"
                :format :vdi
                :variants [:fixed]}))

  NOTE: not all formats and variants are supported for all hosts, nor
  all combinations of variatns are valid. Error reporting on this
  front is spotty at best"
  [server {:keys [size format variants location] :as image-spec}]
  (session/with-vbox server [_ vbox]
    (image/create-medium vbox location format size variants)))

(defn instance* [server name image machine & [base-folder]]
    (let [uuid (:uuid image)
          os-type-id (:os-type-id image)]
      (create-machine server name os-type-id machine uuid base-folder)))

(defn instance [server name image-key-or-map machine-key-or-map & [base-folder]]
  {:pre [(model/Server? server)]}
  (update-models)
  (let [image (if (keyword? image-key-or-map)
                (image-key-or-map *images*)
                image-key-or-map)
        config-overrides (:hardware image)
        config (if (keyword? machine-key-or-map)
                 (machine-key-or-map *machine-models*)
                 machine-key-or-map)
        config (merge config config-overrides)]
    (when-not image
      (throw (RuntimeException.
              (format
               "manager/instance: Image model is not valid or not found: %s."
               image-key-or-map))))
    (when-not config
      (throw (RuntimeException.
              (format
               "manager/instance: Hardware model is not valid or not found: %s."
                machine-key-or-map))))
    (log/infof "Instantiating VM with image: %s hardware: %s" image config)
    (instance* server name image config base-folder)))

;;; machine control
(defn current-time-millis []
  (System/currentTimeMillis))

(defn wait-for-machine-state [m state-keys & [timeout-in-ms]]
  {:pre [(model/Machine? m)]}
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

(defn wait-for-lockable-session-state
  "Wait for the machine to be in a state which could be locked.
   Returns true if wait succeeds, nil otherwise."
  [m & [timout-in-ms]]
  (let [end-time (+ (current-time-millis) (or timout-in-ms 1500))
        unlocked? (fn []
                    (let [state (or (try
                                      (session/with-session
                                        m :read [s _] (.getState s))
                                      (catch Exception _))
                                    SessionState/Locked)]
                      (log/tracef
                       "wait-for-lockable-session-state: state %s" state)
                      (if (= SessionState/Locked state)
                        (Thread/sleep 250)
                        true)))]
    (loop []
      (if (< (current-time-millis) end-time)
        (if (unlocked?)
          true
          (recur))
        nil))))

(defn state [^Machine m]
  (session/with-no-session m [vb-m] (machine/state vb-m)))

(defn start
  [^Machine m & opt-kv]
  {:pre [(model/Machine? m)]}
  (let [server (:server m)
        machine-id (:id m)]
    (session/with-vbox server [mgr vbox]
      (apply machine/start mgr vbox machine-id opt-kv))))

(defn stop
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (let [return-val
        (session/with-session m :shared [s _]
          (machine/stop (.getConsole s)))]
    ;; When using the XPCOM bridge, it seems that power-down gets
    ;; the session stuck. Recreating the session seems to fix it!
    (when (xpcom?)
      (session/with-session m :shared [s _]))
    ;; return the return value of the first call
    return-val))

(defn pause
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (session/with-session m :shared [s _]
    (machine/pause (.getConsole s))))

(defn resume
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (session/with-session m :shared [s _]
    (machine/resume (.getConsole s))))

(defn power-down
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (try+
   (session/with-session m :shared [s _]
     (machine/power-down (.getConsole s)))
   ;; don't return until the VM is in a lockable state. power-down
   ;; doesn't seem to be always returning the IProgress object, and
   ;; when it does, waiting for the operation to finish doesn't seem
   ;; to result in a lockable VM, so we just wait for a lockable state
   ;; by polling. Ugly, I know.
   (wait-for-lockable-session-state m)
   (catch [:type :vbox-runtime] _
     (log/warn "Trying to stop an already stopped machine"))))

(defn destroy [machine & {:keys [delete-disks timeout]
                          :or {delete-disks true
                               timeout -1}}]
  {:pre [(model/Machine? machine)]}
  (let [id (:id machine)]
    (session/with-no-session machine [vb-m]
      (let [media (machine/unregister vb-m :detach-all-return-hard-disks-only)]
        (.waitForCompletion
         (machine/delete vb-m (if delete-disks media nil))
         (Integer. (int timeout)))))))

(defn nuke
  "Powers down a machine and destroys it"
  [machine & options]
  {:pre [(model/Machine? machine)]}
  (try (power-down machine)
       (catch Exception e nil))
  (try (destroy machine)
       true
       (catch Exception e false)))

(defn send-keyboard [machine entries]
  "Given a sequence with a mix of character strings and keywords it
 sends to the machine the scan codes via the virtual keyboard that
 correspond to the values in 'entries'.

 (chars) will provide a list of permitted characters in the strings
 (non-chars) will provide a list of the permitted commands as keywords

 Example:
  (scan-codes {:keypad-5 \"Abc\"})
  => (76 204 42 30 158 170 48 176 46 174)"
  (session/with-session machine :shared [s _]
    (machine/send-keyboard-entries s entries)))


;;; virtualbox-wide functions

(defn hard-disks [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getHardDisks ^IVirtualBox vbox)))))

(defn machines [server & groups]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [_ vbox]
    (let [machines
          (if groups
            (.getMachinesByGroups ^IVirtualBox vbox groups)
            (.getMachines ^IVirtualBox vbox))]
      (doall (map #(model/dry % server) machines)))))

(defn managed-machines [server]
  (machines server "/vmfest"))

(defn get-machine
  "Will raise a condition if machine cannot be found."
  [server id]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [mgr vbox]
    (when-let [vb-m (vbox/find-vb-m vbox id)]
      (model/dry vb-m server))))

(defn find-machine [server id-or-name]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [mgr vbox]
    (when-let [vb-m (vbox/find-vb-m vbox id-or-name)]
      (model/dry vb-m server))))

(defn guest-os-types [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [mgr vbox]
    (let [get-keys (fn [os-type-map]
                     (select-keys
                      os-type-map
                      [:id :description :family-description :64-bit?]))]
      (map (comp get-keys vmfest.virtualbox.guest-os-type/map-from-IGuestOSType)
           (.getGuestOSTypes ^IVirtualBox vbox)))))

(defn diagnostics
  "Returns a data structure with all the relevant information about
  the state of vmfest and its configuration"
  [server]
  (session/with-vbox server [_ vbox]
    {:virtualbox (vbox/vbox-info vbox)
     :image-models (update-models)
     :hw-models *machine-models*
     :machines (managed-machines server)}))
;;; model forwards

(defn as-map [& params]
  (apply model/as-map params))

(defn make-disk-immutable [server path]
  (session/with-vbox server [_ vbox]
    (let [medium (vbox/find-medium vbox path :hard-disk)]
      (log/infof "Compacting image %s" path)
      (.waitForCompletion
       (.compact medium)
       (Integer. -1))
      (log/infof "Making hard-disk %s multi-attach (immutable)" path)
      (image/make-immutable medium))))

(comment "without pallet-style infrastructure"
  (use 'vmfest.manager)
  (def my-server (server "http://localhost:18083"))
  (guest-os-types my-server) ;; see the list of os guests
  ;; create and start one server with a configuring function
  (def my-machine (create-machine my-server "my-name" "Linux" basic-config))
  (start my-machine)
  ;; create and start many servers
  (def clone-names #{"c1" "c2" "c3" "c4" "c5" "c6"})
  (def my-machines
    (map #(create-machine my-server % "Linux" basic-config) clone-names))
  (map start my-machines)
  (map stop my-machines))

(comment "with pallet-style infrastructure"
  (use 'vmfest.manager)
  ;; create a connection to the vbox server
  (def my-server (server "http://localhost:18083"))

  ;; instantiate a new machine based on a supplied hardware model and image
  (def my-machine (instance my-server "Test-1" :cent-os-5-5 :micro))

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
         (def service
           (pallet.compute/compute-service-from-config-file "virtualbox"))
         (def my-node
           (pallet.core/make-node
            "pallet-test"
            {:image-id :cent-os-5-5 :packager :yum :os-family :centos}))
         (pallet.core/converge {my-node 1} :compute service)
         (pallet.core/converge {my-node 0} :compute service))
