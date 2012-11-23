(ns vmfest.virtualbox.machine
  (:require [clojure.tools.logging :as log]
            [vmfest.virtualbox.virtualbox :as virtualbox]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.enums :as enums]
            [vmfest.virtualbox.session :as session])
  (:use [vmfest.virtualbox.scan-codes :only (scan-codes)])
  (:import [org.virtualbox_4_2 IMachine IConsole VBoxException
            VirtualBoxManager IVirtualBox IMedium NetworkAttachmentType]
           [vmfest.virtualbox.model GuestOsType Machine]))


(def getters
  {:name #(.getName %)
   :description #(.getDescription %)
   :accessible? #(.getAccessible %)
   :access-error #(.getAccessError %) ;; todo. get object
;;;   :os-type #(let [type-id (.getOSTypeId %)
;;;                  object (GuestOsType. type-id server)]
;;;              (model/as-map object))
   :hardware-version #(.getHardwareVersion %)
   :hardware-uuid #(.getHardwareUUID %)
   :cpu-count #(.getCPUCount %)
   :cpu-hot-plug-enabled? #(.getCPUHotPlugEnabled %)
   :memory-size #(.getMemorySize %)
   :memory-ballon-size #(.getMemoryBalloonSize %)
   :page-fusion-enabled? #(.getPageFusionEnabled %)
   :vram-size #(.getVRAMSize %)
   :accelerate-3d-enabled? #(.getAccelerate3DEnabled %)
   :accelerate-2d-video-enabled? #(.getAccelerate2DVideoEnabled %)
   :monitor-count #(.getMonitorCount %)
   :bios-settings #(.getBIOSSettings %) ;todo: get object
   :firmware-type #(enums/firmware-type-to-key
                   (.getFirmwareType %)) ;todo: get object
   :pointing-hid-type #(enums/pointing-hid-type-to-key
                       (.getPointingHIDType %)) ;todo: get object
   :keyboard-hid-type #(enums/keyboard-hid-type-to-key
                       (.getKeyboardHIDType %)) ;todo: get object
   :hpet-enabled #(.getHPETEnabled %)
   :snapshot-folder #(.getSnapshotFolder %)
   :vrde-server #(.getVRDEServer %) ;todo: get object
   :medium-attachments #(.getMediumAttachments %) ;todo: get
                                        ;objects
   :usb-controller #(.getUSBController %) ;todo: get object
   :audio-adapter #(.getAudioAdapter %) ; todo: get object
   :storage-controllers #(.getStorageControllers %) ;todo: get
                                        ;objects
   :settings-file-path #(.getSettingsFilePath %)
   :settings-modified? #(try (.getSettingsModified %)
                            (catch Exception e (comment "Do nothing")))
   :session-state #(enums/session-state-to-key
                   (.getSessionState %)) ;todo: get object
   :session-type #(enums/session-type-to-key (.getSessionType %))
   :session-pid #(.getSessionPID %)
   :state #(enums/machine-state-to-key (.getState %)) ;todo: get object
   :last-state-change #(.getLastStateChange %) ;todo: make-date?
   :state-file-path #(.getStateFilePath %)
   :logFolder #(.getLogFolder %)
   :current-snapshot #(.getCurrentSnapshot %)
   :snapshot-count #(.getSnapshotCount %)
   :current-state-modified? #(.getCurrentStateModified %)
   :shared-folders #(.getSharedFolders %) ;todo: get objects
   :clipboard-mode #(enums/clipboard-mode-to-key
                    (.getClipboardMode %)) ;todo: get object
   :guest-property-notification-patterns
   #(.getGuestPropertyNotificationPatterns %)
   :teleporter-enabled? #(.getTeleporterEnabled %)
   :teleporter-port #(.getTeleporterPort %)
   :teleporter-address #(.getTeleporterAddress %)
   :teleporter-password #(.getTeleporterPassword %)
   :rtc-use-utc? #(.getRTCUseUTC %)
   :io-cache-enabled? #(.getIOCacheEnabled %)
   :io-cache-size #(.getIoCacheSize %)
   ;;   :io-bandwidth-max #(.getIoBandwidthMax %)
   })

(defn get-attribute [vb-m getter-key]
  {:pre (model/IMachine? vb-m)}
  (when-let [getter (getter-key getters)]
    (getter vb-m)))

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
                       (.getPointingHIDType vb-m)) ;todo: get object
   :keyboard-hid-type (enums/keyboard-hid-type-to-key
                       (.getKeyboardHIDType vb-m)) ;todo: get object
   :hpet-enabled (.getHPETEnabled vb-m)
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
   :session-pid (.getSessionPID vb-m)
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
   :io-cache-enabled? (.getIOCacheEnabled vb-m)
   :io-cache-size (.getIOCacheSize vb-m)
   ;;   :io-bandwidth-max (.getIoBandwidthMax vb-m)
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
   :pointing-hid-type #(.setPointingHIDType %2 %1)
   :keyboard-hid-type #(.setKeyboardHIDType %2 %1)
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
   :io-cache-enabled? #(.setIOCacheEnabled %2 %1)
   :io-cache-size #(.setIOCacheSize %2 (long %1))
   ;;   :io-bandwidth-max #(.setIoBandwidthMax %2 (long %1))
   })


(defn set-map [m value-map]
  (let [get-setter (fn [k] (k setters))
        set-fn (fn [[k v]]
                 (let [setter (get-setter k)]
                   (if setter
                     (setter v m)
                     (log/errorf
                      "IMachine has no setter defined for %s" k))))]
    (doall (map set-fn value-map))))

(extend-type vmfest.virtualbox.model.Machine
  model/vbox-object
  (soak [this vbox]
        (let [vb-m (virtualbox/find-vb-m vbox (:id this))]
          (log/tracef "soak: soaking %s into %s" this vb-m)
          vb-m))
  (as-map [this]
          (session/with-vbox (:server this) [_ vbox]
            (try
              (let [machine (model/soak this vbox)]
                (merge this
                       (map-from-IMachine machine (:server this))))
              (catch Exception e
                (log/errorf e "as-map: Machine %s not found. Reason" (:id this))
                (merge this
                       {:error "Machine not found"
                        :exception e})))))
  #_(get-attribute
   [this key]
   (when-let [getter (:key map-from-IMachine)]
     (session/with-vbox (:server this) [_ vbox]
       (session/with-no-session this [m]
         (getter m))))))

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
  {:pre [(model/VirtualBoxManager? mgr)
         (model/IVirtualBox? vbox)]}
  (let [opts (apply hash-map opt-kv)
        session (.getSessionObject mgr)
        session-type  (or (:session-type opts) "gui")
        env (or (:env opts) "DISPLAY:0.0")]
    (try
      (conditions/with-vbox-exception-translation
        {:E_UNEXPECTED "Virtual Machine not registered."
         :E_INVALIDARG (format "Invalid session type '%s'" session-type)
         :VBOX_E_OBJECT_NOT_FOUND
         (format "No machine matching id '%s' found." machine-id)
         :VBOX_E_INVALID_OBJECT_STATE "Session already open or opening."
         :VBOX_E_IPTR_ERROR "Launching process for machine failed."
         :VBOX_E_VM_ERROR "Failed to assign machine to session."}
        (let [vb-m (virtualbox/find-vb-m vbox machine-id)
              progress
              (.launchVMProcess vb-m session session-type env)
              #_(.openRemoteSession vbox session machine-id session-type env)]
          (log/debug (str "start: Starting session for VM " machine-id "..."))
          (.waitForCompletion progress (Integer. 30000))
          (let [result-code (.getResultCode progress)]
            (log/debugf "start: VM %s started with result code %s"
                               machine-id
                               result-code)
            result-code)))
      (catch Exception e
        (log/error e "Cannot start machine")
        (conditions/wrap-exception
         e
         {:message "An error occurred while starting machine"})))))


(defn save-settings [machine]
  {:pre [(model/IMachine? machine)]}
  (try
    (conditions/with-vbox-exception-translation
      {:VBOX_E_FILE_ERROR
       "Settings file not accessible while trying to save them"
       :VBOX_E_XML_ERROR "Cannot parse settings XML file"
       :E_ACCESSDENIED "Saving of the settings has been refused"}
      (.saveSettings machine))
    (catch Exception e
      (conditions/wrap-exception
       e
       {:message "An error occurred while saving a machine"}))))


;; TODO: This is redundant with machine-config/add-storage-controller
(defn add-storage-controller  [m name bus-type]
  {:pre [(model/IMachine? m)
         name
         (enums/key-to-storage-bus bus-type)]}
  (let [bus (enums/key-to-storage-bus bus-type)]
    (conditions/with-vbox-exception-translation
      {:VBOX_E_OBJECT_IN_USE
       "A storage controller with given name exists already."
       :E_INVALIDARG "Invalid controllerType."}
      (.addStorageController m name bus))))

#_(defn attach-device [m name controller-port device device-type medium]
  {:pre [(model/IMachine? m)
         name controller-port device
         (instance? IMedium medium)
         (enums/key-to-device-type device-type)]}
  (let [type (enums/key-to-device-type device-type)]
    (conditions/with-vbox-exception-translation
      {:E_INVALIDARG
       "SATA device, SATA port, IDE port or IDE slot out of range."
       :VBOX_E_INVALID_OBJECT_STATE
       "Attempt to attach medium to an unregistered virtual machine."
       :VBOX_E_INVALID_VM_STATE
       "Invalid machine state."
       :VBOX_E_OBJECT_IN_USE
       "Hard disk already attached to this or another virtual machine."}
      (.attachDevice m
                     name
                     (Integer. controller-port)
                     (Integer. device)
                     type
                     medium))))

(defn set-network-adapter [m port type interface]
  {:pre [(model/IMachine? m)
         port interface
         (enums/key-to-network-attachment-type type)]}
  (try
    (if-let [adapter (.getNetworkAdapter m (long port))]
      (condp = type
        :bridged (do (.setAttachmentType adapter NetworkAttachmentType/Bridged)
                     (.setBridgedInterface adapter interface))
        :nat (.setAttachmentType adapter NetworkAttachmentType/NAT)
        :internal (.setAttachmentType adapter NetworkAttachmentType/Internal)
        :host-only (.setAttachmentType adapter NetworkAttachmentType/HostOnly)
        :generic (.setAttachmentType adapter NetworkAttachmentType/Generic)
        ;;todo -- raise a condition
      ))))

(defn stop
  [^IConsole c]
  {:pre [(model/IConsole? c)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_INVALID_VM_STATE "Virtual machine not in Running state."
     :VBOX_E_PDM_ERROR "Controlled power off failed."}
    (log/tracef "stop: machine-id: %s" (.getId (.getMachine c)))
    (.powerButton c)))

(defn pause
  [^IConsole c]
  {:pre [(model/IConsole? c)]}
  (conditions/with-vbox-exception-translation
     {:VBOX_E_INVALID_VM_STATE "Virtual machine not in Running state."
      :VBOX_E_VM_ERROR "Virtual machine error in suspend operation."}
    (.pause c)))

(defn resume
  [^IConsole c]
  {:pre [(model/IConsole? c)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_INVALID_VM_STATE "Virtual machine not in Paused state."
        :VBOX_E_VM_ERROR "Virtual machine error in resume operation."}
    (.resume c)))

(defn power-down
  [^IConsole c]
  {:pre [(model/IConsole? c)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_INVALID_VM_STATE
     "Virtual machine must be Running, Paused or Stuck to be powered down."}
    (let [progress (.powerDown c)]
      (.waitForCompletion progress (Integer. 30000)))))


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
        (.detachDevice vb-m name (Integer. controller-port)
                       (Ingteger. device))
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
  {:pre [(model/IConsole? console)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_INVALID_VM_STATE "Machine session is not open."}
    (.getGuestPropertyValue (.getMachine console) key)))

(defn set-guest-property [^IMachine machine key value]
  {:pre [(model/IMachine? machine)]}
  (conditions/with-vbox-exception-translation
    {:E_ACCESSDENIED "Property cannot be changed."
     :VBOX_E_INVALID_VM_STATE
     "Virtual machine is not mutable or session not open."
     :VBOX_E_INVALID_OBJECT_STATE
     "Cannot set transient property when machine not running."}
    (.setGuestPropertyValue machine key value)))

(defn set-extra-data [^IMachine m key value]
  {:pre [(model/IMachine? m)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_FILE_ERROR "Settings file not accessible."
     :VBOX_E_XML_ERROR "Could not parse the settings file."}
    (.setExtraData m key value)
    (.saveSettings m)))

(defn get-extra-data [^IMachine m key]
  {:pre [(model/IMachine? m)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_FILE_ERROR "Settings file not accessible."
     :VBOX_E_XML_ERROR "Could not parse the settings file."}
    (.getExtraData m key)))

(defn get-extra-data-keys [^IMachine m]
  {:pre [(model/IMachine? m)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_FILE_ERROR "Settings file not accessible."
     :VBOX_E_XML_ERROR "Could not parse the settings file."}
    (.getExtraDataKeys m)))

;;;;;;;

(defn unregister [vb-m & [cleanup-key]]
  {:pre [(model/IMachine? vb-m)
         (if cleanup-key (enums/key-to-cleanup-mode cleanup-key) true)]}
  ;; todo, make sure the key is valid
  (conditions/with-vbox-exception-translation
    {:VBOX_E_INVALID_OBJECT_STATE
     "Machine is currently locked for a session."}
    (let [cleanup-mode (if cleanup-key
                         (enums/key-to-cleanup-mode cleanup-key)
                         :unregister-only)]
      (log/infof
       "unregister: unregistering machine with name %s with cleanup %s"
       (.getName vb-m)
       cleanup-mode)
      (.unregister vb-m cleanup-mode))))

(defn delete [vb-m media]
  {:pre [(model/IMachine? vb-m)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_INVALID_VM_STATE
     "Machine is registered but not write-locked."
     :VBOX_E_IPRT_ERROR
     "Could not delete the settings file."}
    (log/infof "delete: deleting machine %s and it's media" (.getName vb-m))
    (.delete vb-m media)))


(defn state [^IMachine vb-m]
  {:pre [(model/IMachine? vb-m)]}
  (enums/machine-state-to-key (.getState vb-m)))

(defn get-storage-controller-by-name [m name]
  {:pre [(model/IMachine? m)]}
  (.getStorageControllerByName m name))

;;; scan codes
(defn send-keyboard-entries [vb-m entries]
  "Given a sequence with a mix of:
     - strings corresponding to text
     - keywords corresponding to non-char key presses
     - numbers corresponding to wait times in ms.
  it sends the scan codes corresponding to either the strings or the keywords
  to the VM's keyboard interface, simulating a keyboard attached to the VM.

  The scan codes will be sent as fast as possible, observing the
  pauses in ms. especified in the form of numbers in the entries
  sequence.

  Note that:
   - (chars) will provide a list of permitted characters in the strings
   - (non-chars) will provide a list of the permitted commands as keywords

 Example:
  (scan-codes {:esc 100 \"Abc\"})
  will send an ESCAPE, wait for 100ms and then send the text \"Abc\" "
  (let [keyboard  (.getKeyboard (.getConsole vb-m))
        scan-code-seq (scan-codes entries)]
    (log/debugf "Sending to %s the scan-codes %s" (.getName (.getMachine vb-m)) scan-code-seq)
    (doseq [sc scan-code-seq]
      (if (number? sc)
        (.putScancode keyboard (Integer. sc))
        (Thread/sleep (first sc))))))