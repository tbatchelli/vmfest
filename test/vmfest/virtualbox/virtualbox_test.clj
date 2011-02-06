(ns vmfest.virtualbox.virtualbox-test
  (:use vmfest.virtualbox.virtualbox :reload-all)
  (:use clojure.test
        vmfest.virtualbox.session
        vmfest.virtualbox.session-test)
  (:import vmfest.virtualbox.model.Server))

(def machine-name-1 "Test-1")
(def machine-name-bogus "bogus name")

(deftest ^{:integration true}
  get-a-machine
  (with-vbox *server* [_ vbox]
    (testing "finding a specific machine on a vbox (by id)"
      (is (not (nil? (find-vb-m vbox machine-name-1)))))
    (testing "trying to find a non-existing machine returns null"
      (is (nil? (find-vb-m vbox machine-name-bogus))))))
