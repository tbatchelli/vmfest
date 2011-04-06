(ns vmfest.manager
  (:require [vmfest.virtualbox.virtualbox :as vbox]
           [vmfest.virtualbox.machine :as machine]
           [vmfest.virtualbox.session :as session]
           [vmfest.virtualbox.model :as model]
           [clojure.contrib.condition :as condition]
           [clojure.tools.logging :as log]
           [clojure.java.io :as io]
           vmfest.virtualbox.medium)
  (:use clojure.contrib.condition)
  (:import org.virtualbox_4_0.SessionState))

(defn server [url & [identity credentials]]
  (vmfest.virtualbox.model.Server. url (or identity "") (or credentials "")))

(def *locations*
  {:local {:model-path "/Users/tbatchlelli/.vmfest/models"
           :node-path "/Users/tbatchelli/.vmfest/nodes"}})

(def *location* (:local *locations*))

;; machine configuration stuff

(defn add-ide-controller [m]
  {:pre [(model/IMachine? m)]}
  (machine/add-storage-controller m "IDE Controller" :ide))

(defn attach-device [m name controller-port device device-type uuid]
  {:pre [(model/IMachine? m)]}
  (machine/attach-device m name controller-port device device-type uuid))

(defn set-bridged-network [m interface]
  {:pre [(model/IMachine? m)]}
  (machine/set-network-adapter m 0 :bridged interface)
  (.saveSettings m))

(defn configure-machine [vb-m param-map]
  {:pre [(model/IMachine? vb-m)]}
  (machine/set-map vb-m param-map)
  (.saveSettings vb-m))

(defn basic-config [m]
  {:pre [(model/IMachine? m)]}
  (let [parameters
        {:memory-size 512
         :cpu-count 1}]
    (configure-machine m parameters)
    (set-bridged-network m "en1: AirPort 2")
    (add-ide-controller m)))

(defn attach-hard-disk [m uuid]
  {:pre [(model/Machine? m)]}
  (session/with-vbox (:server m) [_ vbox]
    (let [medium (vbox/find-medium vbox uuid)]
      (session/with-session m :shared [_ vb-m]
        (try
          (attach-device vb-m "SATA Controller" 0 0 :hard-disk medium)
          (catch clojure.contrib.condition.Condition _
            (attach-device vb-m "IDE Controller" 0 0 :hard-disk medium)))
        (.saveSettings vb-m)))))

(defn get-ip [machine]
  {:pre [(model/Machine? machine)]}
  (session/with-session machine :shared [session _]
    (machine/get-guest-property
     (.getConsole session)
     "/VirtualBox/GuestInfo/Net/0/V4/IP")))

(defn set-extra-data [machine key value]
  {:pre [(model/Machine? machine)]}
  (session/with-session machine :write [_ vb-m]
    (machine/set-extra-data vb-m key value)))

(defn get-extra-data [machine key]
  {:pre [(model/Machine? machine)]}
  (log/tracef "get-extra-data: getting extra data for %s %s" (:id machine) key)
  (session/with-no-session machine [vb-m]
    (machine/get-extra-data vb-m key)))

(defn get-machine-attribute [machine key]
  (session/with-no-session machine [m]
    (machine/get-attribute m key)))

;;; jclouds/pallet-style infrastructure

(def *machine-models*
  {:micro basic-config})

(def *images*
  {:cent-os-5-5
   {:description "CentOS 5.5 32bit"
    :uuid "/Users/tbatchelli/Library/VirtualBox/HardDisks/Test1.vdi"
    :os-type-id "RedHat"}
   :ubuntu-10-10-64bit
   {:description "Ubuntu 10.10 64bit"
    :uuid "/Users/tbatchelli/VBOX-HDS/Ubuntu-10-10-64bit.vdi"
    :os-type-id "Ubuntu_64"}})

(defn create-machine
  [server name os-type-id config-fn image-uuid & [base-folder]]
  {:pre [(model/Server? server)]}
  (when-let [f (or base-folder (:node-path *location*))]
    (when-not (.exists (io/file f))
      (condition/raise
       :type :path-not-found
       :message (format "Path for saving nodes doesn't exist: %s" f))))
  (let [m (session/with-vbox server [_ vbox]
            (let [machine (vbox/create-machine
                           vbox
                           name
                           os-type-id
                           true ;; overwrite whatever previous
                           ;; definition was there
                           (or base-folder (:node-path *location*)))]
              (config-fn machine)
              (machine/save-settings machine)
              (vbox/register-machine vbox machine)
              (vmfest.virtualbox.model.Machine. (.getId machine) server nil)))]
    ;; can't set the drive
    (attach-hard-disk m image-uuid)
    m))

(defn instance [server name image-key machine-key & [base-folder]]
  {:pre [(model/Server? server)]}
  (let [image (image-key *images*)
        config-fn (machine-key *machine-models*)]
    (when-not (and image config-fn)
      (throw (RuntimeException. "Image or Machine not found")))
    (let [uuid (:uuid image)
          os-type-id (:os-type-id image)]
      (create-machine server name os-type-id config-fn uuid base-folder))))

;;; machine control
(defn current-time-millis []
  (System/currentTimeMillis))

(defn wait-for-machine-state [m state-keys & [timeout-in-ms]]
  {:pre [(model/Machine? m)]}
  (let [begin-time (current-time-millis)
        timeout (or timeout-in-ms 1500)
        target-state? (fn [state-key] (some #(= state-key %) state-keys))]
    (loop []
      (let [current-state (session/with-no-session m [vb-m]
                            (machine/state vb-m))]
        (if (target-state? current-state)
          current-state
          (if (> (- (current-time-millis) begin-time) timeout)
            nil
            (do
              (Thread/sleep 1000)
              (recur))))))))

(defn wait-for-lockable-session-state
  "Wait for the machine to be in a state which could be locked.
   Returns true if wait succeeds, nil otherwise."
  [m & [timout-in-ms]]
  (let [end-time (+ (current-time-millis) timout-in-ms)]
    (loop []
      (let [state (session/with-session m :write [s _] (.getState s))]
        (if  (not= SessionState/Locked state)
          (if (< (current-time-millis) end-time)
            (do
              (log/tracef "wait-for-lockable-session-state: state %s" state)
              (Thread/sleep 250)
              (recur))
            nil)
          true)))))

(defn state [^Machine m]
  (session/with-no-session m [vb-m] (machine/state vb-m)))

(defn start
  [^Machine m & opt-kv]
  {:pre [(model/Machine? m)]}
  (let [server (:server m)
        machine-id (:id m)]
    (session/with-vbox server [mgr vbox]
      (apply machine/start mgr vbox machine-id opt-kv))))

(defn stop
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (session/with-session m :shared [s _]
    (machine/stop (.getConsole s))))

(defn pause
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (session/with-session m :shared [s _]
    (machine/pause (.getConsole s))))

(defn resume
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (session/with-session m :shared [s _]
    (machine/resume (.getConsole s))))

(defn power-down
  [^Machine m]
  {:pre [(model/Machine? m)]}
  (handler-case :type
    (session/with-session m :shared [s _]
      (machine/power-down (.getConsole s)))
    (handle :vbox-runtime
      (log/warn "Trying to stop an already stopped machine"))))

;; just keeping the code around in case the new implementation using
;; the new vbox 4.0 clean-up features don't work as expected.
;; toni 20110213
#_(defn destroy [machine]
  (let [vbox (:server machine)]
    (try
      (let [settings-file (get-machine-attribute machine :settings-file-path)]
        (let [progress (power-down machine)]
          (when progress
            (.waitForCompletion progress -1)))
        (session/with-session machine :write [_ vb-m]
          (machine/remove-all-media vb-m))
        (session/with-vbox vbox [_ vbox]
          (vbox/unregister-machine vbox machine))
        (.delete (java.io.File. settings-file))))))

(defn destroy [machine]
  {:pre [(model/Machine? machine)]}
  (let [id (:id machine)]
    (session/with-no-session machine [vb-m]
      (let [media (machine/unregister vb-m :detach-all-return-hard-disks-only)]
        (machine/delete vb-m media)))))


;;; virtualbox-wide functions

(defn hard-disks [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getHardDisks vbox)))))

(defn machines [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [_ vbox]
    (doall (map #(model/dry % server) (.getMachines vbox)))))

(defn get-machine
  "Will raise a condition if machine cannot be found."
  [server id]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [mgr vbox]
    (when-let [vb-m (vbox/find-vb-m vbox id)]
      (model/dry vb-m server))))

(defn find-machine [server id-or-name]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [mgr vbox]
    (when-let [vb-m (vbox/find-vb-m vbox id-or-name)]
      (model/dry vb-m server))))

(defn guest-os-types [server]
  {:pre [(model/Server? server)]}
  (session/with-vbox server [mgr vbox]
    (let [get-keys (fn [os-type-map]
                     (select-keys
                      os-type-map
                      [:id :description :family-description :64-bit?]))]
      (map (comp get-keys vmfest.virtualbox.guest-os-type/map-from-IGuestOSType)
           (.getGuestOSTypes vbox)))))

;;; model forwards

(defn as-map [& params]
  (apply model/as-map params))

(comment "without pallet-style infrastructure"
  (use 'vmfest.manager)
  (def my-server (server "http://localhost:18083"))
  (guest-os-types my-server) ;; see the list of os guests
  ;; create and start one server with a configuring function
  (def my-machine (create-machine my-server "my-name" "Linux" basic-config))
  (start my-machine)
  ;; create and start many servers
  (def clone-names #{"c1" "c2" "c3" "c4" "c5" "c6"})
  (def my-machines
    (map #(create-machine my-server % "Linux" basic-config) clone-names))
  (map start my-machines)
  (map stop my-machines))

(comment "with pallet-style infrastructure"
  (use 'vmfest.manager)
  ;; create a connection to the vbox server
  (def my-server (server "http://localhost:18083"))

  ;; instantiate a new machine based on a supplied hardware model and image
  (def my-machine (instance my-server "Test-1" :cent-os-5-5 :micro))

  ;; operate the machine
  (start my-machine)
  (pause my-machine)
  (resume my-machine)
  (stop my-machine) ;; tells the OS to shut down
  (power-down my-machine) ;; turns off the machine
  ;; destroy the machine. All files will be deleted (irrecoverable)
  (destroy my-machine)

  ;; list available hard drives
  (hard-disks my-server)
  ;; view all info about the hard-drives
  (map as-map (hard-disks my-server))

  ;;view all available machines
  (machines my-server)
  ;;get a machine by name or id
  (def my-test-machine (find-machine my-server "CentOS Test")))

(comment "From pallet"
         "start swank with $ mvn -Pno-jclouds,vmfest clojure:swank"
         (require 'pallet.core)
         (def service
           (pallet.compute/compute-service-from-config-file "virtualbox"))
         (def my-node
           (pallet.core/make-node
            "pallet-test"
            {:image-id :cent-os-5-5 :packager :yum :os-family :centos}))
         (pallet.core/converge {my-node 1} :compute service)
         (pallet.core/converge {my-node 0} :compute service))
