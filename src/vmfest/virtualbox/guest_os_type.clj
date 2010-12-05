(ns vmfest.virtualbox.guest-os-type
  (:require [clojure.contrib.logging :as log]
            [vmfest.virtualbox.session :as session]
            [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2 IGuestOSType]))

(defn map-from-IGuestOSType
  [o]
  {:family-id (.getFamilyId o)
   :family-description (.getFamilyDescription o)
   :id (.getId o)
   :description (.getDescription o)
   :64-bit? (.isIs64Bit o)
   :recommended-io-apic? (.isRecommendedIOAPIC o)
   :recommended-virt-ex? (.isRecommendedVirtEx o)
   :recommended-ram (.getRecommendedRAM o)
   :recommended-vram (.getRecommendedVRAM o)
   :recommended-hdd (.getRecommendedHDD o)
   :adapter-type (.getAdapterType o) ;todo: pull the object
   :recommended-pae? (.isRecommendedPae o)
   :recommended-dvd-storage-controller (.getRecommendedDvdStorageController o) ;todo: pull object
   :recommended-dvd-storage-bus (.getRecommendedDvdStorageBus o) ;todo: pull object
   :recommended-hd-storage-controller (.getRecommendedHdStorageController o) ;todo: pull object
   :recommended-hd-storage-bus (.getRecommendedHdStorageBus o) ;todo: pull object
   :recommended-firmware (.getRecommendedFirmware o) ;todo: pull object
   :recommended-usb-hid? (.isRecommendedUsbHid o)
   :recommended-hpet? (.isRecommendedHpet o)
   :recommended-usb-tablet? (.isRecommendedUsbTablet o)
   :recommended-rtc-use-utc? (.isRecommendedRtcUseUtc o)
   })


(extend-type vmfest.virtualbox.model.GuestOsType
  model/vbox-object
  (soak [this vbox]
        (.getGuestOSType vbox (:id this)))
  (as-map [this]
          (session/with-vbox (:server this) [_ vbox]
            (let [object (model/soak this vbox)]
              (merge this
                     (map-from-IGuestOSType object))))))