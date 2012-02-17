(ns vmfest.virtualbox.machine-config
  (:use [slingshot.slingshot :only [throw+]])
  (:require [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.enums :as enums]
            [vmfest.virtualbox.model :as model]
            [clojure.tools.logging :as log]
            [vmfest.virtualbox.virtualbox :as vbox])
  (:import [org.virtualbox_4_1 AccessMode VBoxException NetworkAttachmentType
            HostNetworkInterfaceType DeviceType]))

(defn get-medium [m device]
  (let [vbox (.getParent m)
        location (:location device)
        device-type (:device-type device)]
    (vbox/find-medium vbox location device-type)))

(def controller-type-checkers
  {:scsi #{:lsi-logic :bus-logic}
   :ide #{:piix3 :piix4 :ich6}
   :sata #{:intel-ahci}
   :sas #{:lsi-logic-sas}})

(defn check-controller-type [bus controller-type]
  ((bus controller-type-checkers) controller-type))

(defn add-storage-controller [m name storage-bus-key & [controller-type-key]]
  (when (and controller-type-key
             (not (check-controller-type storage-bus-key controller-type-key)))
    (throw+ {:type :machine-builder
                 :message
                 (format "Bus of type %s is not compatible with controller %s"
                         storage-bus-key
                         controller-type-key)}))
  (let [storage-bus (enums/key-to-storage-bus storage-bus-key)
        controller-type (when controller-type-key
                          (enums/key-to-storage-controller-type
                           controller-type-key))]
    (log/debugf "add-storage-controller: m=%s name='%s' storage-bus %s"
                m name storage-bus)
    (let [sc (.addStorageController m name storage-bus)]
      (when controller-type
        (.setControllerType sc controller-type)))))

(defn attach-device [m controller-name device port slot]
  {:pre (model/IMachine? m)}
  (log/debugf
   "attach-device: Attaching %s in controller %s slot %s port %s for machine %s"
   device controller-name slot port (.getName m))
  (let [device-type (enums/key-to-device-type (:device-type device))
        medium (get-medium m device)]
    (when-not device-type
      (throw+
       {:type :machine-builder
        :message (str "Failed to attach device; it is missing a"
                      " valid device-type entry")}))
    (.attachDevice m
                   controller-name
                   (Integer. port)
                   (Integer. slot)
                   device-type
                   medium)))

(defmulti attach-devices
  (fn [m bus-type controller-name devices] bus-type))

(defmethod attach-devices :ide [m bus-type controller-name devices]
  (log/debugf
   "attach-devices: Attaching devices to %s in controller %s: %s"
   (.getName m) controller-name devices)
  (doall
   (map (fn [device slot port]
          (when device
            (attach-device m controller-name device port slot)))
        devices [0 1 0 1] [0 0 1 1])))

(defn simple-attach-devices [m controller-name devices]
  (log/debugf
   "attach-devices: Attaching devices to %s in controller %s: %s"
   (.getName m) controller-name devices)
  (doall (map (fn [device port]
                (when device
                  (attach-device m controller-name device port 0)))
              devices (range (count devices)))))

(defmethod attach-devices :sata [m bus-type controller-name devices]
  (simple-attach-devices m controller-name devices))

(defmethod attach-devices :sas [m bus-type controller-name devices]
  (simple-attach-devices m controller-name devices))

(defmethod attach-devices :scsi [m bus-type controller-name devices]
  (simple-attach-devices m controller-name devices) )

(defn configure-controller [m config]
  (log/debugf
   "configure-controller: Configuring controller for machine '%s': %s"
   (.getName m) config)
  (let [{:keys [name bus devices type]} config
        controller (add-storage-controller m name bus type)]
    (attach-devices m bus name devices)))

(defn configure-storage [m config]
  (log/debugf "Configuring storage for machine %s: %s"
              (.getName m) config)
  (doseq [controller-config config]
    (log/debugf "Configuring controller for %s" controller-config)
    (when controller-config (configure-controller m controller-config))))


;;; network

(defn set-adapter-property [adapter property-key value]
  (when value
    (condp = property-key
        :adapter-type (.setAdapterType adapter value)
        :network (.setNetwork adapter value)
        ;; equiv. to bridged-interface 4.0.x and below
        :host-interface (.setBridgedInterface adapter value)
        :bridged-interface (.setBridgedInterface adapter value)
        :internal-network (.setInternalNetwork adapter value)
        :host-only-interface (.setHostOnlyInterface adapter value)
        :enabled (.setEnabled adapter value)
        :cable-connected (.setCableConnected adapter value)
        :mac-address (.setMACAddress adapter value)
        :line-speed (.setLineSpeed adapter value)
        :nat-driver (log/error "Setting NAT driver not supported")
        :attachment-type nil ;; do nothing
        (log/errorf "set-adapter-property: unknown property %s" property-key))))

(defn configure-adapter-object [adapter config]
  (let [nat-forwards (:nat-forwards config)
        config (dissoc config :nat-forwards)
        ;; enable the adapters by default
        config (assoc config :enabled (or (:enabled config) true))]
    (doseq [[k v] config]
      (set-adapter-property adapter k v))))

(defn attach-to-bridged [adapter]
  (.setAttachmentType adapter NetworkAttachmentType/Bridged))

(defn attach-to-nat [adapter]
  (.setAttachmentType adapter NetworkAttachmentType/NAT))

(defn attach-to-host-only [adapter machine]
  (let [host (.getHost (.getParent machine))
        host-only-ifs
        (.findHostNetworkInterfacesOfType
         host
         HostNetworkInterfaceType/HostOnly)
        host-if-names
        (map #(.getName %) host-only-ifs)
        if-name
        (.getHostOnlyInterface adapter)]
    (when-not (some (partial = if-name) host-if-names)
      (log/error
       (format
        (str
         "Trying to configure a network adapter with inexistent host interface named %s"
         " for machine %s")
        if-name (.getName machine)))))
  (.setAttachmentType adapter NetworkAttachmentType/HostOnly))

(defn attach-to-internal [adapter]
  (.setAttachmentType adapter NetworkAttachmentType/Internal))

(defn attach-adapter [machine adapter attachment-type]
  (condp = attachment-type 
        :bridged (attach-to-bridged adapter)
        :nat (attach-to-nat adapter)
        :internal (attach-to-internal adapter)
        :host-only (attach-to-host-only adapter machine)
        :vde (log/error "Setting up VDE interfaces is not yet supported.")
        (log/errorf
         "configure-adapter: unrecognized attachment type %s" attachment-type)))

(defn configure-adapter
  [m slot config]
  (let [attachment-type (:attachment-type config)]
    (log/debugf
     "configure-adapter: Configuring network adapter for machine '%s' slot %s with %s"
     (.getName m) slot config)
    (let [adapter (.getNetworkAdapter m (long slot))]
      (configure-adapter-object adapter config)
      (attach-adapter m adapter attachment-type))))

(defn configure-network [m config]
  (log/debugf "Configuring network for machine %s with %s"
              (.getName m) config)
  (let [vbox (.getParent m)
        system-properties (.getSystemProperties vbox)
        adapter-count (.getMaxNetworkAdapters system-properties (.getChipsetType m))
        adapter-configs (partition 2 (interleave (range adapter-count) config))]
    (doseq [[slot adapter-config] adapter-configs]
      (log/debugf "Configuring adapter %s with %s" slot adapter-config)
      (when adapter-config (configure-adapter m slot adapter-config)))))

(defn configure-machine [m config]
  (doseq [[entry value] config]
    (condp = entry
        :network (configure-network m value)
        :storage nil ;; ignored. 
        :boot-mount-point nil ;; ignored.
        (if-let [setter (entry machine/setters)]
          (setter value m)
          (log/warnf
           "There is no such setting %s in a machine configuration"
           entry)))))

(defn configure-machine-storage [m {:keys [storage]}]
   (when storage
    (configure-storage m storage)))

(defn attach-storage [m config]
  (when-let [storage-config (:storage config)]
    (configure-storage m storage-config)))
