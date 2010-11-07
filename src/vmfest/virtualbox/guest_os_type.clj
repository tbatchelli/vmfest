(ns vmfest.virtualbox.guest-os-type
  (:use [clojure.contrib.logging :as log]
        [vmfest.virtualbox.virtualbox :as virtualbox]
        [vmfest.virtualbox.vbox :as vbox]
        [vmfest.util :as util]
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2 IGuestOSType]))

(defn map-from-IGuestOSType
  [o]
  {:family-id (.getFamilyId o)
   :family-description (.getFamilyDescription o)
   :id (.getId o)
   :description (.getDescription o)
   :is64Bit? (.isIs64Bit o)
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


(extend-type vmfest.virtualbox.model.guest-os-type
  model/vbox-object
  (soak [this]
        (let [[_ vbox] (virtualbox/create-mgr-vbox (:server this))
              uuid (:id this)]
          (.getGuestOSType vbox uuid)))
  (as-map [this]
          (let [object (soak this)]
            (merge this
                   (map-from-IGuestOSType object)))))