(ns vmfest.virtualbox.machine
  (:use [clojure.contrib.logging :as log]
        [vmfest.virtualbox.session :as session]
        [vmfest.virtualbox.virtualbox :as virtualbox]
        [vmfest.virtualbox.conditions :as conditions]
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2 IMachine]
           [vmfest.virtualbox.model GuestOsType Machine]))

(defn map-from-IMachine
  [^IMachine vb-m server]
  {:name (.getName vb-m)
   :description (.getDescription vb-m)
   :acessible? (.getAccessible vb-m)
   :access-error (.getAccessError vb-m) ;; todo. get object
   :os-type (let [type-id (.getOSTypeId vb-m)
                  object (GuestOsType. type-id server)]
              (as-map object))
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
   :firmware-type (.getFirmwareType vb-m) ;todo: get object
   :pointing-hid-type (.getPointingHidType vb-m) ;todo: get object
   :keyboard-hid-type (.getKeyboardHidType vb-m) ;todo: get object
   :hpet-enabled (.getHpetEnabled vb-m)
   :snapshot-folder (.getSnapshotFolder vb-m)
   :vrdp-server (.getVRDPServer vb-m) ;todo: get object
   :medium-attachments (.getMediumAttachments vb-m) ;todo: get
                                 ;objects
   :usb-controller (.getUSBController vb-m) ;todo: get object
   :audio-adapter (.getAudioAdapter vb-m) ; todo: get object
   :storage-controllers (.getStorageControllers vb-m) ;todo: get
                                 ;objects
   :settings-file-path (.getSettingsFilePath vb-m)
   :settings-modified? (try (.getSettingsModified vb-m)
                            (catch Exception e (comment "Do nothing")))
   :session-state (.getSessionState vb-m) ;todo: get object
   :session-type (.getSessionType vb-m)
   :session-pid (.getSessionPid vb-m)
   :state (.getState vb-m) ;todo: get object
   :last-state-change (.getLastStateChange vb-m) ;todo: make-date?
   :state-file-path (.getStateFilePath vb-m)
   :logFolder (.getLogFolder vb-m)
   :current-snapshot (.getCurrentSnapshot vb-m)
   :snapshot-count (.getSnapshotCount vb-m)
   :current-state-modified? (.getCurrentStateModified vb-m)
   :shared-folders (.getSharedFolders vb-m) ;todo: get objects
   :clipboard-mode (.getClipboardMode vb-m) ;todo: get object
   :guest-property-notification-patterns (.getGuestPropertyNotificationPatterns vb-m)
   :teleporter-enabled? (.getTeleporterEnabled vb-m)
   :teleporter-port (.getTeleporterPort vb-m)
   :teleporter-address (.getTeleporterAddress vb-m)
   :teleporter-password (.getTeleporterPassword vb-m)
   :rtc-use-utc? (.getRTCUseUTC vb-m)
   :io-cache-enabled? (.getIoCacheEnabled vb-m)
   :io-cache-size (.getIoCacheSize vb-m)
   :io-bandwidth-max (.getIoBandwidthMax vb-m)
   })

(extend-type vmfest.virtualbox.model.Machine
  model/vbox-object
  (soak [this vbox]
        (virtualbox/find-vb-m vbox (:id this)))
  (as-map [this]
          (session/with-vbox (:server this) [_ vbox]
            (let [machine (soak this vbox)]
              (merge this
                     (map-from-IMachine machine (:server this)))))))

(extend-type IMachine
  model/vbox-remote-object
  (dry [this server]
       (let [id (.getId this)]
         (vmfest.virtualbox.model.Machine. id server nil))))

(defn start
  [^Machine machine]
  (session/with-vbox (:server machine) [mgr vbox]
    (let [machine-id (:id machine)
          session (.getSessionObject mgr vbox)
          session-type  "gui"
          env "DISPLAY:0.0"]
      (try (let [progress (.openRemoteSession vbox session machine-id session-type env)]
             (debug (str "Starting session for VM " machine-id "..."))
             (.waitForCompletion progress 10000)
             (let [result-code (.getResultCode progress)]
               (if (zero? result-code)
                 nil
                 true)))
           (catch Exception e#
             (conditions/log-and-raise e# :error "An error occurred" :unknown))))))

(defn stop 
  [^Machine m]
  (session/with-remote-session m [_ machine]
    (.powerButton machine)))

(defn pause
  [^Machine m]
  (session/with-remote-session m [_ machine]
    (.pause machine)))

(defn resume
  [^Machine m]
  (session/with-remote-session m [_ machine]
    (.resume machine)))
;;;;;;;