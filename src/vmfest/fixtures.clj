(ns vmfest.fixtures
  (:require [vmfest.virtualbox.virtualbox :as vbox]
            [vmfest.virtualbox.machine :as machine]
            [vmfest.virtualbox.model :as model])
  (:use [fs.core :only (delete-dir)]
        vmfest.virtualbox.session)
  (:import [vmfest.virtualbox.model Server Machine]))

(def ^:dynamic *dir* "/tmp/vbox-tests")
(def ^:dynamic *url* "http://localhost:18083")
(def ^:dynamic *username* "")
(def ^:dynamic *password* "")
(def ^:dynamic *server* (Server. *url* *username* *password*))
(def ^:dynamic *test-machine-name* "test-created-machine")

(defn initialize-test-dir []
  (clojure.java.io/make-parents *dir*)
  (try
    (delete-dir *dir*)
    (catch Exception e)))

(defn get-new-test-machine
  ([]
     (get-new-test-machine *test-machine-name*))
  ([name]
     (with-vbox *server* [mgr vbox]
       (get-new-test-machine name vbox)))
  ([name vbox]
     (if-let [machine (vbox/find-vb-m vbox name)]
       (machine/unregister machine :full))
     (let [machine
           (vbox/create-machine vbox (str "ZZZ-" name) "RedHat" true *dir*)]
       (machine/save-settings machine)
       (vbox/register-machine vbox machine)
       (model/dry machine *server*))))

(defn delete-test-machine
  [name]
  (let [name (str "ZZZ-" name)
        machine (with-vbox *server* [mgr vbox]
                  (model/dry
                   (vbox/find-vb-m vbox name)
                   *server*))]
    (with-no-session machine [vb-m]
      (let [media (machine/unregister vb-m :full)]
        (machine/delete vb-m media)))))
