(ns vmfest.virtualbox.machine-config-test
  (:use vmfest.virtualbox.machine-config :reload)
  (:use clojure.test
        vmfest.virtualbox.session)
  (:require [vmfest.fixtures :as fixtures]
            [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.enums :as enums]
            [clojure.tools.logging :as log])
  (:import [slingshot ExceptionInfo]))

(def boot-hd
  {:device-type :hard-disk
   :location "/Users/tbatchelli/Library/VirtualBox/HardDisks/Test1.vdi"
   :attachment-type :multi-attach} )

(def test-storage-controllers
  {:storage-controllers
   [{:name "IDE Controller"
     :bus :ide
     :type :ich6
     :bootable true ;; NOT IMPLEMENTED YET!
     :devices [boot-hd
               {:device-type :dvd}]}
    {:name "SATA controller"
     :bus :sata
     :type :intel-ahci
     :bootable :true}]})

(def test-network-adapters
  [{:attachment-type :nat ;;default? ;; NetworkAttachmentType
    :adapter-type nil ;; NetworkAdapterTypes
    :network "name" ;; only if attachment type is :nat :internal :vde
    :host-interface "en1: Airport 2" ;;only if attachment type is :host
    ;; host interface can come from IHost::getNetworkInterfaces
    :enabled true ;; default
    :cable-connected true ;;default
    :mac-address "231q334"
    :line-speed 123 ;; in kbps
    :nat-driver "TBD"}]) ;; tbd (not implemented)
  

(deftest ^{:integration true}
  configuration-function-tests
  (let [machine (fixtures/get-new-test-machine "configuration-tests")]
    (with-session machine :write [_ m]
      (add-storage-controller m "storage-controller" :sata)
      (is (.getStorageControllerByName m "storage-controller")))))

(defmacro with-config-machine [& body]
  `(let [machine# (fixtures/get-new-test-machine "machine-build-test")]
     (try
       (with-session machine# :write [_# ~'m]
         ~@body)
       (finally (fixtures/delete-test-machine "machine-build-test")))))

(defn find-device [m controller-name port slot]
  (let [attachments (.getMediumAttachments m)
        match (fn [attachment]
                (let [controller-name (.getController attachment)
                      port (.getPort attachment)
                      slot (.getDevice attachment)]
                  (log/debug
                   (format
                    "find-device: looking at controller %s port %s slot %s"
                    controller-name port slot))
                  (when (and controller-name port slot)
                    (log/debug "find-device: device found!")
                    true)))]
    (when-not attachments
      (log/warn
       (format "find-device: no attachments found for %s"
               (.getName m))))
    (some match attachments)))

(deftest ^{:integration true}
  storage-controller-building-test
  (testing "A machine can be configured with-empty-storage"
    (with-config-machine
      (is (nil? (some #(not (nil? %))
                          (machine/get-attribute m :storage-controllers))))
          (configure-storage m [])
          (.saveSettings m)
          (is (nil? (some #(not (nil? %))
                          (machine/get-attribute m :storage-controllers))))))
  (testing "Storage controllers are configured in a machine"
    (with-config-machine
      (add-storage-controller m "storage-controller" :sata)
      (let [sc (machine/get-storage-controller-by-name m "storage-controller")]
        (is sc "The storage controller is created")
        (is (= :sata (enums/storage-bus-to-key (.getBus sc))))))
    (testing "configure storage controller from dsl"
      (with-config-machine
        (let [controller-config
              {:name "IDE Controller"
               :bus :ide
               :type :piix3
               :bootable true
               :devices nil}]
          (configure-controller m controller-config)
          (let [sc (machine/get-storage-controller-by-name m "IDE Controller")]
            (is sc "The storage controller is created")
            (is (= :ide (enums/storage-bus-to-key (.getBus sc)))))))))
  (testing "Storage and devices are created correctly in an IDE controller"
    (with-config-machine
      (let [config
            [{:name "IDE Controller"
              :bus :ide
              :devices [nil nil {:device-type :dvd}]}]]
        (configure-storage m config)
        (let [device (find-device m "IDE Controller" 1 0)]
          (is device))))
    (with-config-machine
      (log/debug "about to configure a machine")
      (configure-storage m  (:storage-controllers test-storage-controllers))))
  (testing "Storage and devices are created correctly in non-ide controllers"
    (with-config-machine
      (let [config
            [{:name "SATA Controller"
              :bus :sata
              :devices [nil nil {:device-type :dvd}]}]]
        (configure-storage m config)
        (let [device (find-device m "SATA Controller" 2 0)]
          (is device))))
    (with-config-machine
      (let [config
            [{:name "SCSI Controller"
              :bus :scsi
              :devices [nil nil {:device-type :dvd}]}]]
        (configure-storage m config)
        (let [device (find-device m "SCSI Controller" 2 0)]
          (is device)))))
  (testing "the controller type is set correctly"
    (with-config-machine
      (let [config [{:name "SCSI Controller"
                     :bus :scsi
                     :type :lsi-logic}]]
        (configure-storage m config)
        (let [device (.getStorageControllerByName m "SCSI Controller")]
          (is (= :lsi-logic (enums/storage-controller-type-to-key
                             (.getControllerType device)))))))
    (with-config-machine
      (let [config [{:name "SCSI Controller"
                     :bus :scsi
                     :type :piix4}]]
        (is (thrown? ExceptionInfo (configure-storage m config)))))))

(deftest check-tests
  (testing "controller-type vs. bus"
    (is (check-controller-type :scsi :lsi-logic))
    (is (not (check-controller-type :ide :lsi-logic)))
    (is (check-controller-type :sata :intel-ahci))))


(deftest ^{:integration true}
  network-config-tests
  (testing "You can configure one network adapter"
    (with-config-machine
      (let [config
            [{:attachment-type :bridged
              :host-interface "en1: Airport 2"}
             nil
             {:attachment-type :bridged
              :host-interface "nothing"}]]
        (configure-network m config)
        (let [configured-adapter (.getNetworkAdapter m (long 0))]
          (is (= (:bridged
                  (enums/network-attachment-type-to-key
                   (.getAttachmentType configured-adapter)))))
          (is (= "en1: Airport 2"
                 (.getBridgedInterface configured-adapter))))
        (is (not (.getEnabled (.getNetworkAdapter m (long 1))))
            "The nil adapters don't get configured")
        (let [configured-adapter (.getNetworkAdapter m (long 2))]
          (is (= "nothing"
                 (.getBridgedInterface configured-adapter)))))))
  (testing "Host-only interfaces"
    (with-config-machine
      (let [config
            [{:attachment-type :host-only
              :host-only-interface "vboxnet0"}
             nil
             {:attachment-type :bridged
              :host-interface "nothing"}]]
        (configure-network m config)
        (let [configured-adapter (.getNetworkAdapter m (long 0))]
          (is (= (:host-only
                  (enums/network-attachment-type-to-key
                   (.getAttachmentType configured-adapter)))))
          (is (= "vboxnet0"
                 (.getHostOnlyInterface configured-adapter))))
        (is (not (.getEnabled (.getNetworkAdapter m (long 1))))
            "The nil adapters don't get configured")
        (let [configured-adapter (.getNetworkAdapter m (long 2))]
          (is (= "nothing"
                 (.getBridgedInterface configured-adapter))))))))

(deftest ^{:integration true}
  nat-config-tests
  (testing "You can add a NAT adapter"
    (with-config-machine
      (let [config
            [{:attachment-type :nat}]]))))

(def machine-config
  {:cpu-count 2
   :memory-size 555
   :network [{:attachment-type :bridged
              :host-interface "en1: Airport 2"}
             nil
             {:attachment-type :bridged
              :host-interface "nothing"}]
   :storage  [{:name "IDE Controller"
               :bus :ide
               :devices [nil nil {:device-type :dvd}]}]})

(deftest ^{:integration true}
  test-full-machine-config
  (with-config-machine
    (configure-machine m machine-config)
    (configure-machine-storage m machine-config)
    (testing "The machine settings are properly set"
      (is (= (long 555) (machine/get-attribute m :memory-size))
          (= (long 2) (machine/get-attribute m :cpu-count))))
    (testing "The network settings are properly set"
      (let [configured-adapter (.getNetworkAdapter m (long 0))]
        (is (= (:bridged
                (enums/network-attachment-type-to-key
                 (.getAttachmentType configured-adapter)))))
        (is (= "en1: Airport 2"
               (.getBridgedInterface configured-adapter)))))
    (testing "The storage settings are properly set"
      (let [device (find-device m "IDE Controller" 1 0)]
          (is device)))))
