(ns vmfest.virtualbox.machine-config
  (:use [slingshot.slingshot :only [throw+ try+]]
        [vmfest.virtualbox.image :only [ make-immutable]]
        [vmfest.virtualbox.host :only [add-host-only-interface]])
  (:require [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.enums :as enums]
            [vmfest.virtualbox.model :as model]
            [clojure.tools.logging :as log]
            [vmfest.virtualbox.virtualbox :as vbox]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.image :as image])
  (:import [org.virtualbox_4_2 AccessMode VBoxException NetworkAttachmentType
            HostNetworkInterfaceType INetworkAdapter IMedium IMachine
            IHostNetworkInterface INATEngine]))

(defn- create-machine-disk
  [^IMachine m location size format variants]
  (let [name (.getName m)
        _ (log/infof "create-machine-disk m=%s: building disk image in %s"
                     name location)
        ^IVirtualBox vbox (.getParent m)]
    (image/create-medium vbox location format size variants)))

(defn ^IMedium get-medium
  [^IMachine m {:keys [location device-type create?
                       size format variants] :as device}]
  (let [vbox (.getParent m)]
    (if (and create? size)
      (create-machine-disk m location size format variants)
      (when location
        (vbox/find-medium vbox location device-type)))))

(def controller-type-checkers
  {:scsi #{:lsi-logic :bus-logic}
   :ide #{:piix3 :piix4 :ich6}
   :sata #{:intel-ahci}
   :sas #{:lsi-logic-sas}})

(defn check-controller-type [bus controller-type]
  ((bus controller-type-checkers) controller-type))

(defn add-storage-controller
  [^IMachine m ^String name storage-bus-key & [controller-type-key]]
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

(defn attach-device [^IMachine m ^String controller-name device port slot]
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
    (conditions/with-vbox-exception-translation
      {:E_INVALIDARG
       "SATA device, SATA port, IDE port or IDE slot out of range, or file or UUID not found."
       :VBOX_E_INVALID_OBJECT_STATE
       "Machine must be registered before media can be attached."
       :VBOX_E_INVALID_VM_STATE
       "Invalid machine state."
       :VBOX_E_OBJECT_IN_USE
       "A medium is already attached to this or another virtual machine."}
      (.attachDevice m
                     controller-name
                     (Integer. (int port))
                     (Integer. (int slot))
                     device-type
                     medium))))

(defmulti attach-devices
  (fn [m bus-type controller-name devices] bus-type))

(defmethod attach-devices :ide [^IMachine m bus-type controller-name devices]
  (log/debugf
   "attach-devices: Attaching devices to %s in controller %s: %s"
   (.getName m) controller-name devices)
  (doall
   (map (fn [device slot port]
          (when device
            (attach-device m controller-name device port slot)))
        devices [0 1 0 1] [0 0 1 1])))

(defn simple-attach-devices [^IMachine m controller-name devices]
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

(defn configure-controller [^IMachine m config]
  (log/debugf
   "configure-controller: Configuring controller for machine '%s': %s"
   (.getName m) config)
  (let [{:keys [name bus devices type]} config
        controller (add-storage-controller m name bus type)]
    (attach-devices m bus name devices)))

(defn configure-storage [^IMachine m config]
  (log/debugf "Configuring storage for machine %s: %s"
              (.getName m) config)
  (doseq [controller-config config]
    (log/debugf "Configuring controller for %s" controller-config)
    (when controller-config (configure-controller m controller-config))))


;;; network

(defn set-adapter-property [^INetworkAdapter adapter property-key value]
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

(defn attach-to-bridged [^INetworkAdapter adapter]
  (.setAttachmentType adapter NetworkAttachmentType/Bridged))

(defn attach-to-nat [^INetworkAdapter adapter]
  (.setAttachmentType adapter NetworkAttachmentType/NAT))

(defn attach-to-host-only [^INetworkAdapter adapter ^IMachine machine]
  (let [vbox (.getParent machine)
        host (.getHost vbox)
        host-only-ifs (.findHostNetworkInterfacesOfType
                       host
                       HostNetworkInterfaceType/HostOnly)
        host-if-names (map #(.getName ^IHostNetworkInterface %) host-only-ifs)
        if-name (.getHostOnlyInterface adapter)]
    (when-not (some (partial = if-name) host-if-names)
      (log/warnf
       (str
        "Trying to configure a network adapter with inexistent host "
        "interface named %s for machine %s")
       if-name (.getName machine))
      (if (re-matches #"vboxnet[0-9]+" if-name)
        (add-host-only-interface vbox if-name)
        (throw+ {:message
                 (format "Cannot create a host only interface named %s"
                         if-name)}))))
  (.setAttachmentType adapter NetworkAttachmentType/HostOnly))

(defn attach-to-internal [^INetworkAdapter adapter]
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

(defn add-nat-rules
  [adapter rules]
  (let [nat-engine (.getNATEngine adapter)]
    (doseq [r rules]
      (log/debugf "add-nat-rules: adding nat-rule %s for adapter %s"
                  r (.getSlot adapter))
      (.addRedirect nat-engine (:name r) (enums/nat-protocol-type (:protocol r))
                    (:host-ip r) (int (:host-port r)) (:guest-ip r) (int (:guest-port r))))))

(defn remove-nat-rules
  [adapter rules]
  (let [nat-engine (.getNATEngine adapter)]
    (doseq [r rules]
      (log/debugf "remove-nat-rules: removing nat-rule %s from adapter %s"
                  r (.getSlot adapter))
      (.removeRedirect nat-engine (:name r)))))

(defn configure-adapter
  [^IMachine m slot config]
  (let [attachment-type (:attachment-type config)]
    (log/debugf
     "configure-adapter: Configuring network adapter for machine '%s' slot %s with %s"
     (.getName m) slot config)
    (let [adapter (.getNetworkAdapter m (long slot))]
      (configure-adapter-object adapter (dissoc config :nat-rules))
      (attach-adapter m adapter attachment-type)
      (add-nat-rules adapter (:nat-rules config)))))

(defn configure-network [^IMachine m config]
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
  (let [config
        (if (> (:cpu-count config) 1)
          ;; using more than one cpu requires IO APIC enabled
          (assoc config :io-apic-enabled? true)
          config)]
    (doseq [[entry value] config]
        :network (configure-network m value)
        :storage nil ;; ignored.
        :boot-mount-point nil ;; ignored.
        (if-let [setter (entry machine/setters)]
          (setter value m)
          (log/warnf
           "There is no such setting %s in a machine configuration"
           entry)))))
           entry))))))

(defn configure-machine-storage [m {:keys [storage]}]
   (when storage
    (configure-storage m storage)))

(defn attach-storage [m config]
  (when-let [storage-config (:storage config)]
    (configure-storage m storage-config)))
