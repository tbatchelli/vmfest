(ns vmfest.virtualbox.machine-test
  (:require [vmfest.virtualbox.session :as session]
            [vmfest.virtualbox.model :as model]
            [clojure.tools.logging :as log])
  (:use vmfest.virtualbox.machine :reload)
  (:use clojure.test
        vmfest.fixtures
        [clojure.contrib.io :only (delete-file-recursively)])
  (:import clojure.contrib.condition.Condition))

(def test-machine-1
  (vmfest.virtualbox.model.Machine. "Test-1" *server* nil))

(deftest ^{:integration true}
  test-vbox-object-protocol-implementation
  (testing "a Machine can be soaked into an IMachine"
    (let [imachine (session/with-vbox *server* [_ vbox]
                     (model/soak test-machine-1 vbox))]
      (is imachine "the machine can be soaked")))
  (testing "a Machine can be turned into a map"
    (let [machine-as-map (model/as-map test-machine-1)]
      (is machine-as-map "the machine can be turn into a map")
      (is (< 0 (:memory-size machine-as-map)) "map values are populated"))))

(deftest ^{:integration true}
  test-vbox-remote-object-implementation
  (testing "an IMachine can be dried into a Machine"
    (session/with-vbox *server* [_ vbox]
      (let [imachine (.findMachine vbox "Test-1")]
        (is imachine "The test machine exists")
        (let [machine (vmfest.virtualbox.model.Machine.
                       (.getId imachine)
                       *server*
                       nil)
              dried-imachine  (model/dry imachine *server*)]
          (is (= (:id machine) (:id dried-imachine))))))))

(deftest ^{:integration true}
  machines-can-start
  (testing "a machine can start"
    (session/with-vbox *server* [mgr vbox]
      (is (start mgr vbox (:id test-machine-1) :session-type "headless"))))
  (testing "you can't start a machine that is already started or starting"
    (session/with-vbox *server* [mgr vbox]
      (is mgr "the manager is created even if the machine is started")
      (is vbox "the virtualbox is created even if the machine is started")
      (is (thrown?
            clojure.contrib.condition.Condition
            (start mgr vbox (:id test-machine-1))))))
  (testing "a running machine cannot be resumed"
    (session/with-session test-machine-1 :shared [s m]
      (is (thrown? Condition (resume (.getConsole s))))))
  (testing "a running machine can be paused"
    (session/with-session test-machine-1 :shared [s m]
      (let [console (.getConsole s)]
        (is (nil? (pause console))))))
  (testing "a paused machine can be resumed"
    (session/with-session test-machine-1 :shared [s m]
      (let [console (.getConsole s)]
        (is (nil? (resume console))))))
  (testing "a running machine can be powered down"
    (Thread/sleep 1000)
    (session/with-session test-machine-1 :shared [s m]
      (let [console (.getConsole s)]
        (is console "You can get a console from a shared session")
        (is (nil? (power-down console))))))
  (testing "a stopped machine cannot be powered down"
    (is (thrown? Condition
                 (session/with-session test-machine-1 :shared [s m]
                   (power-down (.getConsole s))))))
  (testing "a stopped machine cannot be paused"
    (session/with-session test-machine-1 :shared [s m]
      (is (thrown? Condition (pause (.getConsole s))))))
  ;; DISABLED: it takes way too long!
  #_(testing "a running machine can be stopped gracefully"
    (session/with-vbox *server* [mgr vbox]
      (is (start mgr vbox (:id test-machine-1) :session-type "headless")))
    (Thread/sleep 200000) ;; give some time to the OS to come up enough
    ;; so it can catch the shutdown event
    (session/with-session test-machine-1 :shared [s m]
      (is (nil? (stop (.getConsole s))))))
  (testing "attributes can be read from the machine"
    (session/with-no-session test-machine-1 [m]
      (is (get-attribute m :name)))))




(deftest ^{:integration true}
  building-a-machine
  (let [machine (get-new-test-machine "aaaabbbcc")
        sc-name "test-sc"
        device-name "test-device"]
    (try
      (log/debug (format "machine-test: machine id %s server %s"
                         (:id machine)
                         (:server machine)))
      (session/with-session machine :write [_ vb-m]
        (testing "you can add a storage controller"
          (add-storage-controller vb-m sc-name :ide)
          (is (.getStorageControllerByName vb-m sc-name)))
        #_(testing "you can attach a device to a storage controller"
            (attach-device m device-name )))
      (finally
       (when machine
         (delete-test-machine "aaaabbbcc"))))))
