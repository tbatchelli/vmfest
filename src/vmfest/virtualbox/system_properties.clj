(ns vmfest.virtualbox.system-properties
  (:import [org.virtualbox_4_2
            ISystemProperties IMachine IVirtualBox IMediumFormat]))

(defmulti ^ISystemProperties system-properties class)

(defmethod system-properties IVirtualBox [^IVirtualBox vbox]
  (.getSystemProperties vbox))

(defmethod system-properties IMachine [^IMachine m]
  (system-properties (.getParent m)))



;;; bulk definition of getters for properties
(defn defproperty [fn-name java-method]
  `(defn ~fn-name [o#]
     (~java-method (system-properties o#))))

(def fn-property-map
  '[min-guest-ram .getMinGuestRAM
    max-guest-ram .getMaxGuestRAM
    min-guest-vram .getMinGuestVRAM
    max-guest-vram .getMaxGuestVRAM
    min-guest-cpu-count .getMinGuestCPUCount
    max-guest-cpu-count .getMaxGuestCPUCount
    max-guest-monitors .getMaxGuestMonitors
    info-vd-size .getInfoVDSize
    serial-port-count .getSerialPortCount
    parallel-port-count .getParallelPortCount
    max-boot-position .getMaxBootPosition
    default-machine-folder .getDefaultMachineFolder
    medium-formats .getMediumFormats
    default-hard-disk-format .getDefaultHardDiskFormat
    free-disk-space-warning .getFreeDiskSpaceWarning
    free-disk-space-error .getFreeDiskSpaceError
    free-disk-space-percent-warning .getFreeDiskSpacePercentWarning
    free-disk-space-percent-error .getFreeDiskSpacePercentError
    vrde-auth-library .getVRDEAuthLibrary
    web-service-auth-library .getWebServiceAuthLibrary
    default-vrde-ext-pack .getDefaultVRDEExtPack
    log-history-count .getLogHistoryCount
    default-audio-driver .getDefaultAudioDriver])


(defmacro properties []
  `(do ~@(for [[fn-name method] (partition 2 fn-property-map)]
            (defproperty fn-name method))))

(properties)

;;; bulk definintion of setters for properties (named as "<fn-name>!")

(def fn-setter-map
  '[default-machine-folder .setDefaultMachineFolder
    default-hard-disk-format .setDefaultHardDiskFormat
    free-disk-space-warning .setFreeDiskSpaceWarning
    free-disk-space-error .setFreeDiskSpaceError
    free-disk-space-percent-warning .setFreeDiskSpacePercentWarning
    free-disk-space-percent-error .setFreeDiskSpacePercentError
    vrde-auth-library .setVRDEAuthLibrary
    web-service-auth-library .setWebServiceAuthLibrary
    default-vrde-ext-pack .setDefaultVRDEExtPack
    log-history-count .setLogHistoryCount])

(defn defsetter [fn-name java-method]
  (let [fn-name (symbol (str fn-name "!"))]
    `(defn ~fn-name [o# p#]
       (~java-method (system-properties o#) p#))))

(defmacro setters []
  `(do ~@(for [[fn-name method] (partition 2 fn-setter-map)]
           (defsetter fn-name method))))

(setters)

(defn supported-medium-formats
  "Returns the supported formats for this vbox (or machine) in the
  form of keywords"
  [o]
  (let [supported-formats (medium-formats o)
        format-ids
        (map #(.getId ^IMediumFormat %) supported-formats)]
    (doall (map #(keyword (.toLowerCase ^String %)) format-ids))))

;; TODO: add missing functions:
;; getDefaultIoCacheSettingForStorageController
;; getDeviceTypesForStorageBus
;; getMaxDevicesPerPortForStorageBus
;; getMaxInstancesOfStorageBus
;; getMaxNetworkAdapters
;; getMaxNetworkAdaptersOfType
;; getMaxPortCountForStorageBus
;; getMinPortCountForStorageBus
