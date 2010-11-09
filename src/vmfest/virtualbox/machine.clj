(ns vmfest.virtualbox.machine
  (:use [clojure.contrib.logging :as log]
        clojure.pprint
        [vmfest.virtualbox.virtualbox :as virtualbox]
        [vmfest.virtualbox.vbox :as vbox]
        [vmfest.util :as util]
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2 IMachine]
           [vmfest.virtualbox.model guest-os-type]))

(defn map-from-IMachine
  [^IMachine machine server]
  {:name (.getName machine)
   :object machine
   :description (.getDescription machine)
   :acessible? (.getAccessible machine)
   :access-error (.getAccessError machine) ;; todo. get object
   :os-type (let [type-id (.getOSTypeId machine)
                     object (guest-os-type. type-id server)]
              (as-map object))
   :hardware-version (.getHardwareVersion machine)
   :hardware-uuid (.getHardwareUUID machine)
   :cpu-count (.getCPUCount machine)
   :cpu-hot-plug-enabled? (.getCPUHotPlugEnabled machine)
   :memory-size (.getMemorySize machine)
   :memory-ballon-size (.getMemoryBalloonSize machine)
   :page-fusion-enabled? (.getPageFusionEnabled machine)
   :vram-size (.getVRAMSize machine)
   :accelerate-3d-enabled? (.getAccelerate3DEnabled machine)
   :accelerate-2d-video-enabled? (.getAccelerate2DVideoEnabled machine)
   :monitor-count (.getMonitorCount machine)
   :bios-settings (.getBIOSSettings machine) ;todo: get object
   :firmware-type (.getFirmwareType machine) ;todo: get object
   :pointing-hid-type (.getPointingHidType machine) ;todo: get object
   :keyboard-hid-type (.getKeyboardHidType machine) ;todo: get object
   :hpet-enabled (.getHpetEnabled machine)
   :snapshot-folder (.getSnapshotFolder machine)
   :vrdp-server (.getVRDPServer machine) ;todo: get object
   :medium-attachments (.getMediumAttachments machine) ;todo: get
                                 ;objects
   :usb-controller (.getUSBController machine) ;todo: get object
   :audio-adapter (.getAudioAdapter machine) ; todo: get object
   :storage-controllers (.getStorageControllers machine) ;todo: get
                                 ;objects
   :settings-file-path (.getSettingsFilePath machine)
   :settings-modified? (try (.getSettingsModified machine)
                            (catch Exception e (comment "Do nothing")))
   :session-state (.getSessionState machine) ;todo: get object
   :session-type (.getSessionType machine)
   :session-pid (.getSessionPid machine)
   :state (.getState machine) ;todo: get object
   :last-state-change (.getLastStateChange machine) ;todo: make-date?
   :state-file-path (.getStateFilePath machine)
   :logFolder (.getLogFolder machine)
   :current-snapshot (.getCurrentSnapshot machine)
   :snapshot-count (.getSnapshotCount machine)
   :current-state-modified? (.getCurrentStateModified machine)
   :shared-folders (.getSharedFolders machine) ;todo: get objects
   :clipboard-mode (.getClipboardMode machine) ;todo: get object
   :guest-property-notification-patterns (.getGuestPropertyNotificationPatterns machine)
   :teleporter-enabled? (.getTeleporterEnabled machine)
   :teleporter-port (.getTeleporterPort machine)
   :teleporter-address (.getTeleporterAddress machine)
   :teleporter-password (.getTeleporterPassword machine)
   :rtc-use-utc? (.getRTCUseUTC machine)
   :io-cache-enabled? (.getIoCacheEnabled machine)
   :io-cache-size (.getIoCacheSize machine)
   :io-bandwidth-max (.getIoBandwidthMax machine)
   })

(extend-type vmfest.virtualbox.model.machine
  model/vbox-object
  (soak [this]
        (let [[_ vbox] (virtualbox/create-mgr-vbox (:server this))
              uuid (:id this)]
          (virtualbox/find-machine vbox uuid)))
  (as-map [this]
          (let [machine (soak this)]
            (merge this
                   (map-from-IMachine machine (:server this))))))

(extend-type IMachine
  model/vbox-remote-object
  (dry [this server]
       (let [id (.getId this)]
         (vmfest.virtualbox.model.machine. id server nil))))