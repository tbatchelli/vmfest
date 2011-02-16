(ns vmfest.virtualbox.machine
  (:use clojure.contrib.condition)
  (:require [clojure.contrib.logging :as log]
            [vmfest.virtualbox.virtualbox :as virtualbox]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.enums :as enums]
            [vmfest.virtualbox.session :as session])
  (:import [org.virtualbox_4_0 IMachine IConsole VBoxException
            VirtualBoxManager IVirtualBox IMedium]
           [vmfest.virtualbox.model GuestOsType Machine]))

(defn map-from-IMachine
  [^IMachine vb-m server]
  {:name (.getName vb-m)
   :description (.getDescription vb-m)
   :acessible? (.getAccessible vb-m)
   :access-error (.getAccessError vb-m) ;; todo. get object
   :os-type (let [type-id (.getOSTypeId vb-m)
                  object (GuestOsType. type-id server)]
              (model/as-map object))
   :hardware-version (.getHardwareVersion vb-m)
   :hardware-uuid (.getHardwareUUID vb-m)
   :cpu-count (.getCPUCount vb-m)
   :cpu-hot-plug-enabled? (.getCPUHotPlugEnabled vb-m)
   :memory-size (.getMemorySize vb-m)
   :memory-ballon-size (.getMemoryBalloonSize vb-m)
   :page-fusion-enabled? (.getPageFusionEnabled vb-m)
   :vram-size (.getVRAMSize vb-m)
   :accelerate-3d-enabled? (.getAccelerate3DEnabled vb-m)
   :accelerate-2d-video-enabled? (.getAccelerate2DVideoEnabled vb-m)
   :monitor-count (.getMonitorCount vb-m)
   :bios-settings (.getBIOSSettings vb-m) ;todo: get object
   :firmware-type (enums/firmware-type-to-key
                   (.getFirmwareType vb-m)) ;todo: get object
   :pointing-hid-type (enums/pointing-hid-type-to-key
                       (.getPointingHidType vb-m)) ;todo: get object
   :keyboard-hid-type (enums/keyboard-hid-type-to-key
                       (.getKeyboardHidType vb-m)) ;todo: get object
   :hpet-enabled (.getHpetEnabled vb-m)
   :snapshot-folder (.getSnapshotFolder vb-m)
   :vrde-server (.getVRDEServer vb-m) ;todo: get object
   :medium-attachments (.getMediumAttachments vb-m) ;todo: get
                                        ;objects
   :usb-controller (.getUSBController vb-m) ;todo: get object
   :audio-adapter (.getAudioAdapter vb-m) ; todo: get object
   :storage-controllers (.getStorageControllers vb-m) ;todo: get
                                        ;objects
   :settings-file-path (.getSettingsFilePath vb-m)
   :settings-modified? (try (.getSettingsModified vb-m)
                            (catch Exception e (comment "Do nothing")))
   :session-state (enums/session-state-to-key
                   (.getSessionState vb-m)) ;todo: get object
   :session-type (enums/session-type-to-key (.getSessionType vb-m))
   :session-pid (.getSessionPid vb-m)
   :state (enums/machine-state-to-key (.getState vb-m)) ;todo: get object
   :last-state-change (.getLastStateChange vb-m) ;todo: make-date?
   :state-file-path (.getStateFilePath vb-m)
   :logFolder (.getLogFolder vb-m)
   :current-snapshot (.getCurrentSnapshot vb-m)
   :snapshot-count (.getSnapshotCount vb-m)
   :current-state-modified? (.getCurrentStateModified vb-m)
   :shared-folders (.getSharedFolders vb-m) ;todo: get objects
   :clipboard-mode (enums/clipboard-mode-to-key
                    (.getClipboardMode vb-m)) ;todo: get object
   :guest-property-notification-patterns
   (.getGuestPropertyNotificationPatterns vb-m)
   :teleporter-enabled? (.getTeleporterEnabled vb-m)
   :teleporter-port (.getTeleporterPort vb-m)
   :teleporter-address (.getTeleporterAddress vb-m)
   :teleporter-password (.getTeleporterPassword vb-m)
   :rtc-use-utc? (.getRTCUseUTC vb-m)
   :io-cache-enabled? (.getIoCacheEnabled vb-m)
   :io-cache-size (.getIoCacheSize vb-m)
                                        ;   :io-bandwidth-max (.getIoBandwidthMax vb-m)
   })

(def setters
  {:name #(.setName %2 %1)
   :description #(.setDescription %2 %1)
   :os-type-id #(.setOSTypeId %2 %1) ;; name is correct?
   :hardware-version #(.setHardwareVersion %2 %1)
   :hardware-uuid #(.setHardwareUUID %2 %1)
   :cpu-count #(.setCPUCount %2 (long %1))
   :cpu-hot-plug-enabled? #(.setCPUHotPlugEnabled %2 %1)
   :memory-size #(.setMemorySize %2 (long %1))
   :memory-balloon-size #(.setMemoryBalloonSize %2 (long %1))
   :page-fusion-enabled? #(.setPageFusionEnabled %2 %1)
   :vram-size #(.setVRAMSize %2 (long %1))
   :accelerate-3d-enabled? #(.setAccelerate3DEnabled %2 %1)
   :accelerate-2d-enabled? #(.setAccelerate2DEnabled %2 %1)
   :monitor-count #(.setMonitorCount %2 (long %1))
   :firmware-type #(.setFirmwareType %2 %1)
   :pointing-hid-type #(.setPointingHidType %2 %1)
   :keyboard-hid-type #(.setKeyboardHidType %2 %1)
   :hpet-enabled #(.setHpetEnabled %2 %1)
   :snapshot-folder #(.setSnapshotFolder %2 %1)
   :clipboard-mode #(.setClipboardMode %2 %1)
   :teleporter-enabled? #(.setTeleporterEnabled %2 %1)
   :guest-property-notification-patters
   #(.setGuestPropertyNotificationPatterns %2 %1)
   :teleporter-port #(.setTeleporterPort %2 %1)
   :teleporter-address #(.setTeleporterAddress %2 %1)
   :teleporter-password #(.setTeleporterAddress %2 %1)
   :rtc-use-utc? #(.setRTCUseUTC %2 %1)
   :io-cache-enabled? #(.setIoCacheEnabled %2 %1)
   :io-cache-size #(.setIoCacheSize %2 (long %1))
   ;;   :io-bandwidth-max #(.setIoBandwidthMax %2 (long %1))
   })


(defn set-map [m value-map]
  (let [get-setter (fn [k] (k setters))
        set-fn (fn [[k v]]
                 (let [setter (get-setter k)]
                   (if setter
                     (setter v m)
                     (log/error
                      (str "IMachine has no setter defined for " k)))))]
    (doall (map set-fn value-map))))

(extend-type vmfest.virtualbox.model.Machine
  model/vbox-object
  (soak [this vbox]
        (let [vb-m (virtualbox/find-vb-m vbox (:id this))]
          (log/trace
           (format "soak: soaking %s into %s" this vb-m))
          vb-m))
  (as-map [this]
          (session/with-vbox (:server this) [_ vbox]
            (try
              (let [machine (model/soak this vbox)]
                (merge this
                       (map-from-IMachine machine (:server this))))
              (catch Exception e
                (log/error (format "as-map: Machine %s not found. Reason"
                                   (:id this)) e)
                (merge this
                       {:error "Machine not found"
                        :exception e}))))))

(extend-type IMachine
  model/vbox-remote-object
  (dry [this server]
       (let [id (.getId this)]
         (vmfest.virtualbox.model.Machine. id server nil))))

(defn start
  "Starts the virtual machine represented by 'machine'.

Optional parameters are:
   :session-type 'gui', 'headless' or 'sdl'. Default is 'gui'
   :env environment as String to be passed to the machine at startup.
See IVirtualbox::openRemoteSession for more details"
  [mgr vbox machine-id & opt-kv]
  {:pre [(instance? VirtualBoxManager mgr)
         (instance? IVirtualBox vbox)]}
  (let [opts (apply hash-map opt-kv)
        session (.getSessionObject mgr)
        session-type  (or (:session-type opts) "gui")
        env (or (:env opts) "DISPLAY:0.0")]
    (try (let [vb-m (virtualbox/find-vb-m vbox machine-id)
               progress
               (.launchVMProcess vb-m session session-type env)
               #_(.openRemoteSession vbox session machine-id session-type env)]
           (log/debug (str "start: Starting session for VM " machine-id "..."))
           (.waitForCompletion progress 30000)
           (let [result-code (.getResultCode progress)]
             (log/debug (format "start: VM %s started with result code %s"
                                machine-id
                                result-code))
             result-code))
         (catch VBoxException e
           (conditions/wrap-vbox-runtime
            e
            {:E_UNEXPECTED
             {:message "Virtual Machine not registered."}
             :E_INVALIDARG
             {:message (format "Invalid session type '%s'" session-type)}
             :VBOX_E_OBJECT_NOT_FOUND
             {:message (format "No machine matching id '%s' found." machine-id)}
             :VBOX_E_INVALID_OBJECT_STATE
             {:message "Session already open or opening."}
             :VBOX_E_IPTR_ERROR
             {:message "Launching process for machine failed."}
             :VBOX_E_VM_ERROR
             {:message "Failed to assign machine to session."}}))
         (catch Exception e
           (log/error "Cannot start machine" e)
           (conditions/log-and-raise
            e
            {:log-level :error
             :message "An error occurred while starting machine"})))))

(defn save-settings [machine]
  {:pre [(instance? IMachine machine)]}
  (try
    (.saveSettings machine)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_FILE_ERROR
        {:message "Settings file not accessible while trying to save them"}
        :VBOX_E_XML_ERROR
        {:message "Cannot parse settings XML file"}
        :E_ACCESSDENIED
        {:message "Saving of the settings has been refused"}}))
    (catch Exception e
      (conditions/log-and-raise
       e
       {:log-level :error
        :message "An error occurred while saving a machine"}))))

(defn add-storage-controller  [m name bus-type]
  {:pre [(instance? IMachine m)
         name
         (enums/key-to-storage-bus bus-type)]}
  (let [bus (enums/key-to-storage-bus bus-type)]
    (try
      (.addStorageController m name bus)
      (catch VBoxException e
        (conditions/wrap-vbox-runtime
         e
         {:VBOX_E_OBJECT_IN_USE
          {:message "A storage controller with given name exists already."}
          :E_INVALIDARG
          {:message "Invalid controllerType."}})))))

(defn attach-device [m name controller-port device device-type medium]
  {:pre [(instance? IMachine m)
         name controller-port device
         (instance? IMedium medium)
         (enums/key-to-device-type device-type)]}
  (let [type (enums/key-to-device-type device-type)]
    (try
      (.attachDevice m name controller-port device type medium)
      (catch VBoxException e
        (conditions/wrap-vbox-runtime
         e
         {:E_INVALIDARG
          {:message
           "SATA device, SATA port, IDE port or IDE slot out of range."}
          :VBOX_E_INVALID_OBJECT_STATE
          {:message
           "Attempt to attach medium to an unregistered virtual machine."}
          :VBOX_E_INVALID_VM_STATE
          {:message "Invalid machine state."}
          :VBOX_E_OBJECT_IN_USE
          {:message
           "Hard disk already attached to this or another virtual machine."}}
         )))))

(defn set-network-adapter [m port type interface]
  {:pre [(instance? IMachine m)
         port interface
         (enums/key-to-network-attachment-type type)]}
  (try
    (if-let [adapter (.getNetworkAdapter m (long port))]
      (condp = type
          :bridged (do (.attachToBridgedInterface adapter)
                       ;; todo: get this from IHost.getNetworkInterfaces
                       (.setHostInterface adapter interface))
          :nat (.attachToNAT adapter)
          :internal (.attachToInternalNetwork adapter)
          :host-only (.attachToHostOnlyInterface adapter)
          :vde (.attachToVDE adapter))
      ;;todo -- raise a condition
      )))

(defn stop
  [^IConsole c]
  {:pre [(instance? IConsole c)]}
  (try
    (log/trace (format "stop: machine-id: %s"
                       (.getId (.getMachine c))))
    (.powerButton c)
    (catch VBoxException e
      (log/debug "stop: Caught exception" e)
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_VM_STATE
        {:message "Virtual machine not in Running state."}
        :VBOX_E_PDM_ERROR
        {:message "Controlled power off failed."}}))))

(defn pause
  [^IConsole c]
  {:pre [(instance? IConsole c)]}
  (try
    (.pause c)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_VM_STATE
        {:message "Virtual machine not in Running state."}
        :VBOX_E_VM_ERROR
        {:message "Virtual machine error in suspend operation."}}))))

(defn resume
  [^IConsole c]
  {:pre [(instance? IConsole c)]}
  (try
    (.resume c)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_VM_STATE
        {:message "Virtual machine not in Paused state."}
        :VBOX_E_VM_ERROR
        {:message "Virtual machine error in resume operation."}}))))


(defn power-down
  [^IConsole c]
  {:pre [(instance? IConsole c)]}
  (try
    (let [progress (.powerDown c)]
      (.waitForCompletion progress 30000))
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_VM_STATE
        {:message
         (str "Virtual machine must be Running, Paused or Stuck to be "
              "powered down.")}}))))


;; ==== DELETE WHEN CONFIRMED IT IS NOT NEEDED ANYMORE =====
#_(defn detach-device [vb-m medium-attachment]
  (println medium-attachment)
  (when (.getMedium medium-attachment)
    (let [device (.getDevice medium-attachment)
          controller-port (.getPort medium-attachment)
          name (.getController medium-attachment)]
      (println "medium:" (.getMedium medium-attachment))
      (println "medium name:" name
               "controller-port:" controller-port
               "device:" device)
      (println "controller:" (.getController medium-attachment))
      (try
        (.detachDevice vb-m name controller-port device)
        (catch Exception e (println e))))))

#_(defn delete-storage [medium-attachment]
  (let [medium (.getMedium medium-attachment)]
    (.deleteStorage medium)))

#_(defn remove-all-media [vb-m]
  (let [medium-attachments (.getMediumAttachments vb-m)
        detach-fn (partial detach-device vb-m)]
    (doall (map detach-fn medium-attachments))
    (.saveSettings vb-m)
    (doall (map delete-storage medium-attachments))))

;; ======================================================

(defn get-guest-property [^IConsole console key]
  {:pre [(instance? IConsole console)]}
  (try
    (.getGuestPropertyValue (.getMachine console) key)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_VM_STATE {:message "Machine session is not open."}}))))

(defn set-guest-property [^IMachine machine key value]
  {:pre [(instance? IMachine machine)]}
  (try
    (.setGuestPropertyValue machine key value)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:E_ACCESSDENIED {:message "Property cannot be changed."}
        :VBOX_E_INVALID_VM_STATE
        {:message "Virtual machine is not mutable or session not open."}
        :VBOX_E_INVALID_OBJECT_STATE
        {:message
         "Cannot set transient property when machine not running."}}))))

(defn set-extra-data [^IMachine m key value]
  {:pre [(instance? IMachine m)]}
  (try
    (.setExtraData m key value)
    (.saveSettings m)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_FILE_ERROR {:message "Sttings file not accessible."}
        :VBOX_E_XML_ERROR {:message "Could not parse the settings file."}}))))

(defn get-extra-data [^IMachine m key]
  {:pre [(instance? IMachine m)]}
  (try
    (.getExtraData m key)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_FILE_ERROR {:message "Settings file not accessible."}
        :VBOX_E_XML_ERROR {:message "Could not parse the settings file."}}))))

;;;;;;;

(defn unregister [vb-m & [cleanup-key]]
  {:pre [(instance? IMachine vb-m)
         (if cleanup-key (enums/key-to-cleanup-mode cleanup-key) true)]}
  ;; todo, make sure the key is valid
  (try
    (let [cleanup-mode (if cleanup-key
                         (enums/key-to-cleanup-mode cleanup-key)
                         :unregister-only)]
      (log/info
       (format "unregister: unregistering machine with name %s with cleanup %s"
               (.getName vb-m)
               cleanup-mode))
      (.unregister vb-m cleanup-mode))
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_OBJECT_STATE
        {:message "Machine is currently locked for a session."}}))))

(defn delete [vb-m media]
  {:pre [(instance? IMachine vb-m)]}
  (try
    (log/info
     (format "delete: deleting machine %s and it's media"
             (.getName vb-m)))
    (.delete vb-m media)
    (catch VBoxException e
      (conditions/wrap-vbox-runtime
       e
       {:VBOX_E_INVALID_VM_STATE
        {:message "Machine is registered but not write-locked."}
        :VBOX_E_IPRT_ERROR
        {:message "Could not delete the settings file."}}))))


(defn state [^IMachine vb-m]
  {:pre [(instance? IMachine vb-m)]}
  (enums/machine-state-to-key (.getState vb-m)))