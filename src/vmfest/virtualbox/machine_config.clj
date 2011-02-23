(ns vmfest.virtualbox.machine-config
  (:require [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.enums :as enums]
            [vmfest.virtualbox.model :as model]
            [clojure.contrib.logging :as log]
            [vmfest.virtualbox.virtualbox :as vbox]
            [clojure.contrib.condition :as cond])
  (:import [org.virtualbox_4_0 AccessMode VBoxException]))

(defn get-medium [m device]
  (let [vbox (.getParent m)
        location (:location device)
        device-type (enums/key-to-device-type (:type device))]
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
    (cond/raise {:type :machine-builder
            :message
                 (format "Bus of type %s is not compatible with controller %s"
                         storage-bus-key
                         controller-type-key)}))
  (let [storage-bus (enums/key-to-storage-bus storage-bus-key)
        controller-type (when controller-type-key
                          (enums/key-to-storage-controller-type
                           controller-type-key))]
    (log/debug (format "add-storage-controller: m=%s name='%s' storage-bus %s"
                       m name storage-bus))
    (let [sc (.addStorageController m name storage-bus)]
       (when controller-type
      (.setControllerType sc controller-type)))))

(defn attach-device [m controller-name device port slot]
  {:pre (model/IMachine? m)}
  (log/debug (format
              (str "attach-device: Attaching %s in controller %s"
                   " slot %s port %s for machine %s")
              device controller-name slot port (.getName m)))
  (let [device-type (enums/key-to-device-type (:device-type device))
        medium (get-medium m device)]
    (when-not device-type
      (cond/raise
       {:type :machine-builder
        :message (str "Failed to attach device; it is missing a"
                      " valid dvice-type entry")}))
    (.attachDevice m controller-name port slot device-type medium)))

(defmulti attach-devices
  (fn [m bus-type controller-name devices] bus-type))

(defmethod attach-devices :ide [m bus-type controller-name devices]
  (log/debug
   (format "attach-devices: Attaching devices to %s in controller %s: %s"
           (.getName m) controller-name devices))
  (doall
   (map (fn [device slot port]
          (when device
            (attach-device m controller-name device port slot)))
        devices [0 1 0 1] [0 0 1 1])))

(defn simple-attach-devices [m controller-name devices]
  (log/debug
   (format "attach-devices: Attaching devices to %s in controller %s: %s"
           (.getName m) controller-name devices))
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
  (log/debug
   (format "configure-controller: Configuring controller for machine '%s': %s"
           (.getName m) config))
  (let [{:keys [name bus devices type]} config
        controller (add-storage-controller m name bus type)]
    (attach-devices m bus name devices)))

(defn configure-storage [m config]
  (log/debug (format "Configuring storage for machine %s: %s"
                     (.getName m) config))
  (doseq [controller-config config]
    (log/debug (format "Configuring controller for %s" controller-config))
    (when controller-config (configure-controller m controller-config))))