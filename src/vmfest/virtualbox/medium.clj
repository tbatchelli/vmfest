(ns vmfest.virtualbox.medium
  (:require [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.virtualbox :as virtualbox]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.session :as session])
  (:import [org.virtualbox_4_1 IMedium]))

(defn map-from-IMedium
  [^IMedium m server]
  {:id (.getId m)
   :description (.getDescription m)
   :state (.getState m)
   :location (.getLocation m)
   :name (.getName m)
   :device-type (.getDeviceType m)
   :host-drive (.getHostDrive m)
   :size (.getSize m)
   :format (.getFormat m)
   :medium-format (.getMediumFormat m)
   :type (.getType m)
   :parent (.getParent m)
   :children (.getChildren m)
   :base (.getBase m)
   :logical-size (.getLogicalSize m)
   :auto-reset (.getAutoReset m)
   :last-access-error (.getLastAccessError m)
   :machine-ids (.getMachineIds m)})

(extend-type vmfest.virtualbox.model.HardDisk
  model/vbox-object
  (soak [this vbox]
        (let [hd (virtualbox/find-medium vbox (:id this))]
          (.refreshState hd)
          hd))
  (as-map [this]
          (session/with-vbox (:server this) [_ vbox]
            (let [medium (model/soak this vbox)]
              (merge this (map-from-IMedium medium (:server this)))))))

(extend-type IMedium
  model/vbox-remote-object
  (dry [this server]
       (let [id (.getId this)]
         (vmfest.virtualbox.model.HardDisk. id server))))
