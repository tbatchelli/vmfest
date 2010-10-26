(ns vmfest.virtualbox.host
  (:use vmfest.virtualbox.vbox
        clojure.contrib.logging))

;;; README
;; this file contains host operations (on IVirtualBox)


(defn machines [vb-m]
  "returns a sequence of IMachines corresponding to the machines available in the virutal box"
  (let [vbox (get-vbox vb-m)]
    (.getMachines vbox)))



(defn start
  ([vb-m]
     (let [uuid (:machine-id vb-m)
           vbox (get-vbox vb-m)
           session (get-session vb-m)
           session-type "gui"
           env "DISPLAY:0.0"]
       (try (let [progress (.openRemoteSession vbox session uuid session-type env)]
              (debug (str "Starting session for VM " uuid "..."))
              (.waitForCompletion progress 10000)
              (let [result-code (.getResultCode progress)]
                (if (zero? result-code)
                  nil
                  true))))))
  ([hostname port username password machine-name-or-id]
     (let [vb-m (build-vbox-machine hostname port username password machine-name-or-id)]
       (start vb-m))))