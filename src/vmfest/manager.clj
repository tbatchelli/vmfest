(ns vmfest.manager
  (:require [vmfest.virtualbox.virtualbox :as vbox]
            [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.machine-config :as machine-config]
            [vmfest.virtualbox.session :as session]
            [vmfest.virtualbox.model :as model]
            [clojure.contrib.condition :as condition]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            vmfest.virtualbox.medium)
  (:use clojure.contrib.condition)
  (:import org.virtualbox_4_0.SessionState java.io.File))

(defn server [url & [identity credentials]]
  (vmfest.virtualbox.model.Server. url (or identity "") (or credentials "")))

(def user-home (System/getProperty "user.home"))

(defn default-model-path
  "Return the default model-path for images"
  [& {:keys [home] :or {home user-home}}]
  (.getPath (io/file home ".vmfest" "models")))

(defn default-node-path
  "Return the default node-path for images"
  [& {:keys [home] :or {home user-home}}]
  (.getPath (io/file home ".vmfest" "nodes")))

(def ^{:dynamic true} *locations*
  {:local {:model-path (default-model-path)
           :node-path (default-node-path)}})

(def ^{:dynamic true} *location* (:local *locations*))


(defn get-ip [machine]
  {:pre [(model/Machine? machine)]}
  (session/with-session machine :shared [session _]
    (machine/get-guest-property
     (.getConsole session)
     "/VirtualBox/GuestInfo/Net/0/V4/IP")))

(defn set-extra-data [machine key value]
  {:pre [(model/Machine? machine)]}
  (session/with-session machine :write [_ vb-m]
    (machine/set-extra-data vb-m key value)))

(defn get-extra-data [machine key]
  {:pre [(model/Machine? machine)]}
  (log/tracef "get-extra-data: getting extra data for %s %s" (:id machine) key)
  (session/with-no-session machine [vb-m]
    (machine/get-extra-data vb-m key)))

(defn get-machine-attribute [machine key]
  (session/with-no-session machine [m]
    (machine/get-attribute m key)))

;;; jclouds/pallet-style infrastructure

(def ^{:dynamic true} *machine-models*
  {:micro {:memory-size 512
           :cpu-count 1
           :network [{:attachment-type :bridged
                      :host-interface "en1: Airport 2"}]
           :storage [{:name "IDE Controller"
                      :bus :ide
                      :devices [nil nil {:device-type :dvd} nil]}]
           :boot-mount-point ["IDE Controller" 0]}})

(def ^{:dynamic true} *images* nil)

(defn fs-dir [path]
  (seq (.listFiles (java.io.File. path))))

(defn image-meta-file? [^java.io.File file]
  (.. (.getName file) (endsWith ".meta")))

(defn read-meta-file [^java.io.File file]
  (try
    (read-string (slurp file))
    (catch Exception e
      (log/warn (format "Wrong file format %s. Skipping" (.getName file))))))

(defn load-models
  "Return a map of all image metadata from the model-path"
  [& {:keys [model-path] :or {model-path (:model-path *location*)}}]
  (let [meta-files (filter image-meta-file? (fs-dir model-path))]
    (reduce merge {} (map read-meta-file meta-files))))

(defn update-models
  "Update model metadata in *images*"
  [& {:keys [model-path] :or {model-path (:model-path *location*)}}]
  (alter-var-root #'*images* (fn [_] (load-models :model-path model-path))))

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
                          ;; When the storage bus is named with storage-bus-name,
                          ;; mount the image in the right device
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

(defn create-machine
  [server name os-type-id config image-uuid & [base-folder]]
  {:pre [(model/Server? server)]}
  (when-let [f (or base-folder (:node-path *location*))]
    (when-not (.exists (io/file f))
      (condition/raise
       :type :path-not-found
       :message (format "Path for saving nodes doesn't exist: %s" f))))
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
          (machine-config/configure-machine-storage vb-m boot-image-mounted-config)
          (machine/save-settings vb-m))))
    m))

(defn instance [server name image-key machine-key & [base-folder]]
  {:pre [(model/Server? server)]}
  (let [image (image-key *images*)
        config (machine-key *machine-models*)]
    (when-not (and image config)
      (throw (RuntimeException. "Image or Machine not found")))
    (let [uuid (:uuid image)
          os-type-id (:os-type-id image)]
      (create-machine server name os-type-id config uuid base-folder))))

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
  (let [end-time (+ (current-time-millis) timout-in-ms)]
    (loop []
      (let [state (session/with-session m :write [s _] (.getState s))]
        (if  (not= SessionState/Locked state)
          (if (< (current-time-millis) end-time)
            (do
              (log/tracef "wait-for-lockable-session-state: state %s" state)
              (Thread/sleep 250)
              (recur))
            nil)
          true)))))

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
  (session/with-session m :shared [s _]
    (machine/stop (.getConsole s))))

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
  (handler-case :type
    (session/with-session m :shared [s _]
      (machine/power-down (.getConsole s)))
    (handle :vbox-runtime
      (log/warn "Trying to stop an already stopped machine"))))

;; just keeping the code around in case the new implementation using
;; the new vbox 4.0 clean-up features don't work as expected.
;; toni 20110213
#_(defn destroy [machine]
  (let [vbox (:server machine)]
    (try
      (let [settings-file (get-machine-attribute machine :settings-file-path)]
        (let [progress (power-down machine)]
          (when progress
            (.waitForCompletion progress -1)))
        (session/with-session machine :write [_ vb-m]
          (machine/remove-all-media vb-m))
        (session/with-vbox vbox [_ vbox]
          (vbox/unregister-machine vbox machine))
        (.delete (java.io.File. settings-file))))))

(defn destroy [machine]
  {:pre [(model/Machine? machine)]}
  (let [id (:id machine)]
    (session/with-no-session machine [vb-m]
      (let [media (machine/unregister vb-m :detach-all-return-hard-disks-only)]
        (machine/delete vb-m media)))))


;;; virtualbox-wide functions

(defn hard-disks [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getHardDisks vbox)))))

(defn machines [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getMachines vbox)))))

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
