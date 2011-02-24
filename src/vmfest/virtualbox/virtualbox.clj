(ns vmfest.virtualbox.virtualbox
  (:require [clojure.contrib.logging :as log]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.enums :as enums]
            vmfest.virtualbox.guest-os-type)
  (:import [org.virtualbox_4_0
            VirtualBoxManager
            IVirtualBox
            VBoxException]
           [vmfest.virtualbox.model
            Server
            Machine]))

(defn find-vb-m
  [vbox id-or-name]
  {:pre [(model/IVirtualBox? vbox)]}
  (try
    (log/trace (format "find-vb-m: looking for machine '%s'" id-or-name))
    (let [vb-m (.findMachine vbox id-or-name)]
      (log/debug (format "find-vb-m: found machine '%s': %s"
                         id-or-name
                         vb-m))
      vb-m)
    (catch Exception e
      (log/warn (format "find-vb-m: Machine identified by '%s' not found."
                        id-or-name)))))

(defn find-medium
  [vbox id-or-location & [type]]
  {:pre [(model/IVirtualBox? vbox)
         (if type (#{:hard-disk :floppy :dvd} type) true)]}
  (if (and type (not (#{:hard-disk :floppy :dvd} type)))
    ;; todo: throw condition
    (log/warn
     (format "find-medium: medium type %s not in #{:hard-disk :floppy :dvd}"
             type))
    (let [type-key (or type :hard-disk)
          type (enums/key-to-device-type type-key)]
      (try (.findMedium vbox id-or-location type)
           (catch Exception e
             (log/warn
              (format "Can't find a medium of type %s located in/with id '%s'."
                      type
                      id-or-location)))))))

(defn register-machine [vbox machine]
  {:pre [(model/IVirtualBox? vbox)
         (model/IMachine? machine)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_OBJECT_NOT_FOUND "No matching virtual machine found"
     :VBOX_E_INVALID_OBJECT_STATE
     "Virtual machine was not created within this VirtualBox instance."}
    (.registerMachine vbox machine)))

(defn create-machine
  ([vbox name os-type-id]
     (create-machine vbox name os-type-id false))
  ([vbox name os-type-id overwrite]
     (create-machine vbox name os-type-id overwrite nil))
  ([vbox name os-type-id overwrite base-folder]
     {:pre [(model/IVirtualBox? vbox)]}
     (let [path (when base-folder
                  (.composeMachineFilename vbox name base-folder))]
       (conditions/with-vbox-exception-translation
         {:VBOX_E_OBJECT_NOT_FOUND "invalid os type ID."
          :VBOX_E_FILE_ERROR
          (str "Resulting settings file name is invalid or the settings"
               " file already exists or could not be created due to an"
               " I/O error.")
          :E_INVALIDARG "name is empty or null."}
         (log/info
          (format
           (str "create-machine: "
                "Creating machine %s in %s, %s overwriting previous contents")
           name
           path
           (if overwrite "" "not")))
          (.createMachine vbox path name os-type-id nil overwrite)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (use '[vmfest.virtualbox.virtualbox :as vbox])

  ;; find by name or UUID
  (def my-machine (vbox/find-machine
                   "http://localhost:18083"
                   ""
                   ""
                   "CentOS Minimal"))
  ;; -> #:vmfest.virtualbox.model.machine{
  ;;           :id "197c694b-fb56-43ed-88f5-f62769134442",
  ;;           :server #:vmfest.virtualbox.model.server{
  ;;                      :username "",
  ;;                      :password ""},
  ;;           :location nil}

  ;; obtain all the attributes of machine
  (use 'vmfest.virtualbox.model)
  (pprint (as-map my-machine))
  ;; -> {:id "197c694b-fb56-43ed-88f5-f62769134442",
  ;;     :server {:url "http://localhost:18083", :username "", :password ""},
  ;;     :location nil,
  ;;     :current-snapshot nil,
  ;;     :cpu-hot-plug-enabled? false,
  ;;     :settings-file-path
  ;;             "/User.../Machines/CentOS Minimal/CentOS Minimal.xml",
  ;;     :hpet-enabled false,
  ;;     :teleporter-port 0,
  ;;     :cpu-count 1,
  ;;     :snapshot-folder
  ;;             "/Users/tbatchelli/Library/VirtualBox/Machines/CentOS
  ;;             Minimal/Snapshots",
  ;; etc.... }

  ;; operate the machine
  (use '[vmfest.virtualbox.machine :as machine])
  (machine/start my-machine)
  (machine/pause my-machine)
  (machine/resume my-machine)
  (machine/stop my-machine)

  ;; query the virtualbox for objects
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (vbox/machines server)

  ;; operate on machines
  (use '[vmfest.virtualbox.session :as session])

  ;; read-only
  (session/with-no-session my-machine [machine]
    (.getMemorySize machine))

  ;; read/write
  (session/with-direct-session my-machine [session machine]
    (.setMemorySize machine (long 1024))
    (.saveSettings machine)))
