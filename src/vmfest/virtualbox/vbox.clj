(ns vmfest.virtualbox.vbox
  (:use clojure.contrib.logging
        [vmfest.virtualbox.virtualbox :as vb]
        [vmfest.util :as util]
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2 IMachine]))


;; {:attribute function-to-set-the-attribute}
;; contains all the settable attributes in IMachine with their setter functions
(defonce *machine-settable-attributes*
  (util/settable-attribute-method-map IMachine))

;; {:attribute function-to-set-the-attribute}
;; contains all the gettable attributes in IMachine with their setter functions
(defonce *machine-gettable-attributes*
  (util/gettable-attribute-method-map IMachine))

(defn set-attributes
  "expecting {:attribute-name [val1 val2 ... valN]}, sets on the
object all the attributes in the map passing the values as parameters
to the setter"
  [attribute-values-map object]
  (let [set-attribute
        (fn [[attribute values]]
          (let [method-fn (attribute *machine-settable-attributes*)]
            (trace (str "set " attribute " = " values))
            (apply method-fn object values)))]
    (doall (map set-attribute attribute-values-map))))

(defn get-attributes
  "expecting [:attribute-name], sets on the object all the attributes
in the map passing the values as parameters to the setter"
  [attribute-keys-vector object]
  (let [get-attribute
        (fn [key]
          (let [method-fn (key *machine-gettable-attributes*)
                value (method-fn object)]
            (trace (str "get " key " = " value))
            [key value]))]
    (doall (into {} (map get-attribute attribute-keys-vector)))))

(defmacro with-server [server vbox-name & body]
  `(try
     (let [{:keys [url# username# password#]} ~server
           [_# ~vbox-name] (create-mgr-vbox url# username# password#)]
       ~@body)
     (catch Exception e#
         (log-and-raise e# :error "while trying to operate a virtualbox" :unknown))))

(defmacro with-vbox [vb-m vbox-name & body]
  `(try
     (let [[_# ~vbox-name] (create-mgr-vbox (:server ~vb-m))]
       ~@body)
     (catch Exception e#
         (log-and-raise e# :error "while trying to operate a virtualbox" :unknown))))


(defn hard-disks [server]
  (with-vbox server vbox
    (seq (.getHardDisks vbox))))

(defn machines [server]
  (with-vbox server vbox
    (seq (.getMachines vbox))))

(comment
  ;; setting a bunch of attributes
  (execute-task my-machine
                (set-attributes-task
                 {:memory-size [(long 1024)]
                  :cpu-count [(long 2)]
                  :name ["A new name"]}))
  ;; getting a bunch of attr
  (execute-task my-machine
                (get-attributes-task
                 [:memory-size
                  :cpu-count 
                  :name ]))
  ;; --> {:memory-size 1024, :cpu-count 1, :name "CentOS Minimal"}
  )
