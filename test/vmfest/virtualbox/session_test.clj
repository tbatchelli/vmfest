(ns vmfest.virtualbox.session-test
  (:use vmfest.virtualbox.session :reload-all)
  (:use [vmfest.virtualbox.virtualbox :only (find-vb-m)])
  (:use clojure.test)
  (:import [org.virtualbox_4_0
            VirtualBoxManager]
           [clojure.contrib.condition
            Condition]
           [vmfest.virtualbox.model
            Server
            Machine]))

(def *url* "http://localhost:18083")
(def *username* "")
(def *password* "")
(def *server* (Server. *url* *username* *password*))

(deftest ^{:integration true}
  ws-session-test
  (let [mgr (create-session-manager)]
    (testing "Get ahold of VBox manager"
      (is (= (class mgr)
             VirtualBoxManager)))
    (testing "Connect to the remote vbox server"
      (let [vbox (create-vbox mgr *url* *username* *password*)]
        (testing "Get a connection to the remote VBox Server"
          (is (not (nil? (.getVersion vbox)))))
        (testing "Connecting to a malformed address should throwh a condition"
          (is (thrown-with-msg? Condition #"Cannot connect"
                (create-vbox mgr "bogus address" "" ""))))))
    (testing "create-mgr-vbox"
      (let [[mgr vbox] (create-mgr-vbox *url* *username* *password*)]
        (is (not (nil? (.getVersion vbox))))))))

(deftest ^{:integration true}
  session-wrappers
  (testing "with-vbox wrapper"
    (is (not (nil? (with-vbox *server* [mgr vbox]
                     (.getVersion vbox)))))))

#_(deftest ^{:integration true}
  get-a-vb-to-test-with
  (def vb-m-1
    (with-vbox *server* [_ vbox]
      (find-vb-m vbox "Test-1")))
  (is (not (nil? vb-m-1))))

(def valid-machine (Machine. "Test-1" *server* nil))


#_(deftest ^{:integration true}
  with-direct-session-test
  (testing "direct session with a valid machine"
    (is (> 0
           (with-direct-session valid-machine [s vb-m]
               (.getMemorySize vb-m))))))




(comment "old tests"
         (ns int.vmfest.virtualbox.session-int
  (:use :reload [vmfest.virtualbox.session]
        [vmfest.virtualbox.virtualbox :only [find-machine]] )
  (:use clojure.contrib.condition)
  (:require :reload [vmfest.virtualbox.model])
  (:import  clojure.contrib.condition.Condition)
  (:use [clojure.test]))

(deftest test-direct-connection
  (let [vb-m (find-machine "http://localhost:18083" "" "" "Test1")]
    (testing "direct session read -- happy path"
      (is (< 0 (with-direct-session vb-m [session machine]
                 (.getMemorySize machine)))))
    (testing "direct session write -- happy path"
      (do (with-direct-session vb-m [session machine]
            (.setMemorySize machine (long 1024))
            (.saveSettings machine)))
      (is (= 1024 (with-direct-session vb-m [session machine]
                    (.getMemorySize machine))))
      (do (with-direct-session vb-m [session machine]
            (.setMemorySize machine (long 512))
            (.saveSettings machine)))
      (is (= 512 (with-direct-session vb-m [session machine]
                   (.getMemorySize machine)))))
    (testing "direct session method call -- wrong method"
      (is (thrown? Condition
                   (with-direct-session vb-m [session machine]
                     (.setBogusVariable machine nil))))
      (is (handler-case :type
                       (with-direct-session vb-m [session machine]
                         (.setBogusVariable machine nil))
                       (handle :invalid-method true)))))))