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
                       (handle :invalid-method true))))))




