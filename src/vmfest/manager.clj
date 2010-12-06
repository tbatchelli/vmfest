(ns vmfest.manager
  (require [vmfest.virtualbox.virtualbox :as vbox]
           [vmfest.virtualbox.machine :as machine]
           [vmfest.virtualbox.session :as session]
           [vmfest.virtualbox.model :as model]))

(defn server [url & [identity credentials]]
  (vmfest.virtualbox.model.Server. url (or identity "") (or credentials "")))

(def *locations*
  {:local {:model-path "/Users/tbatchlelli/.vmfest/models"
           :node-path "/Users/tbatchelli/.vmfest/nodes"}})

(def *location* (:local *locations*))

(defn add-ide-controller [m]
  (machine/add-storage-controller m "IDE Controller" :ide))

(defn attach-device [m name controller-port device device-type uuid]
  (machine/attach-device m name controller-port device device-type uuid))

(defn basic-config [m]
  (let [parameters
        {:memory-size 512
         :cpu-count 1}]
    (machine/set-map* m parameters)
    (add-ide-controller m)))

(defn set-test1-drive [vb-m]
  (session/with-direct-session vb-m [_ m]
    (attach-device m "IDE Controller" 0 0 :hard-disk "3a971213-0482-4eb8-8cfd-7eefc9e8b0fe")
    (.saveSettings m)))

(defn create-machine [server name os-type-id config-fn & [base-folder]]
  (let [m (session/with-vbox server [_ vbox]
            (let [machine (vbox/create-machine vbox name os-type-id (or base-folder (:node-path *location*)))]
              (config-fn machine)
              (machine/save-settings machine)
              (vbox/register-machine vbox machine)
              (vmfest.virtualbox.model.Machine. (.getId machine) server nil)))]
    ;; can't set the drive 
    (set-test1-drive m)
    m))


(comment
  (use 'vmfest.manager)
  (require '[vmfest.virtualbox.virtualbox :as vbox])
  (require '[vmfest.virtualbox.machine :as machine])
  (def my-server (server "http://localhost:18083"))
  (vbox/guest-os-types my-server) ;; see the list of os guests
  ;; create and start one server
  (def my-machine (create-machine my-server "my-name" "Linux" basic-config))
  (machine/start my-machine)
  ;; create and start many servers
  (def clone-names #{"c1" "c2" "c3" "c4" "c5" "c6"})
  (def my-machines (map #(create-machine my-server % "Linux" basic-config) clone-names))
  (map machine/start my-machines)
  (map machine/stop my-machines))