(ns vmfest.manager
  (require [vmfest.virtualbox.virtualbox :as vbox]
           [vmfest.virtualbox.machine :as machine]
           [vmfest.virtualbox.session :as session]))

(defn server [url & [identity credentials]]
  (vmfest.virtualbox.model.Server. url (or identity "") (or credentials "")))

(defn basic-config [m]
  (let [parameters
        {:memory-size 1024
         :cpu-count 2}]
    (machine/set-map* m parameters)))

(defn create-machine [server name os-type-id config-fn & [base-folder]]
  (session/with-vbox server [_ vbox]
    (let [machine (vbox/create-machine vbox name os-type-id base-folder)]
      (config-fn machine)
      (machine/save-settings machine)
      (vbox/register-machine vbox machine))))

(comment
 (def my-server (server "http://localhost:18083"))
 (vbox/get-os-types my-server)
 (create-machine my-server "my-name" "Linux" basic-config))