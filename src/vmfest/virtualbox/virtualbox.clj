(ns vmfest.virtualbox.virtualbox
  (:require [clojure.tools.logging :as log]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.enums :as enums]
            vmfest.virtualbox.guest-os-type)
  (:use [vmfest.virtualbox.version :only [xpcom?]])
  (:import [org.virtualbox_4_3
            VirtualBoxManager
            IMachine
            IMedium
            IVirtualBox
            IHostNetworkInterface
            VBoxException
            IDHCPServer
            IHost]
           [vmfest.virtualbox.model
            Server
            Machine]))

(defn ^IMachine find-vb-m
  [^IVirtualBox vbox ^String id-or-name]
  {:pre [(model/IVirtualBox? vbox)]}
  (try
    (log/tracef "find-vb-m: looking for machine '%s'" id-or-name)
    (let [vb-m (.findMachine vbox id-or-name)]
      (log/debugf "find-vb-m: found machine '%s': %s" id-or-name vb-m)
      vb-m)
    (catch Exception e
      (log/warnf
       "find-vb-m: Machine identified by '%s' not found." id-or-name))))

(defn ^IMedium open-medium
  [^IVirtualBox vbox ^String location & [type access-mode force-new-uuid?]]
  {:pre [(model/IVirtualBox? vbox)
         (if type (#{:hard-disk :floppy :dvd} type) true)]}
  (let [type (enums/key-to-device-type (or type :hard-disk))
        access-mode (enums/key-to-access-mode (or access-mode :read-write))
        force-new-uuid? (or force-new-uuid? false)]
    (conditions/with-vbox-exception-translation
      {:VBOX_E_FILE_ERROR
       "Invalid medium storage file location or could not find the medium
at the specified location."
       :VBOX_E_IPRT_ERROR
       "Could not get medium storage format."
       :E_INVALIDARG
       "Invalid medium storage format."
       :VBOX_E_INVALID_OBJECT_STATE
       "Medium has already been added to a media registry."}
      (.openMedium vbox location type access-mode (boolean force-new-uuid?)))))

(defn ^IMedium find-medium
  [vbox id-or-location & [type]]
  #_{:pre [(model/IVirtualBox? vbox)
         (if type (#{:hard-disk :floppy :dvd} type) true)]}
  #_(if (and type (not (#{:hard-disk :floppy :dvd} type)))
    ;; todo: throw condition
    (log/warnf
     "find-medium: medium type %s not in #{:hard-disk :floppy :dvd}" type)
    (let [type-key (or type :hard-disk)
          type (enums/key-to-device-type type-key)]
      (try (.findMedium vbox id-or-location type)
           (catch Exception e
             (log/warnf "find-medium: location %s not found" id-or-location)))))
  (try (open-medium vbox id-or-location type)
       (catch Exception e
         (log/warnf "find-medium: location %s not found" id-or-location))))

(defn register-machine [^IVirtualBox vbox ^IMachine machine]
  {:pre [(model/IVirtualBox? vbox)
         (model/IMachine? machine)]}
  (conditions/with-vbox-exception-translation
    {:VBOX_E_OBJECT_NOT_FOUND "No matching virtual machine found"
     :VBOX_E_INVALID_OBJECT_STATE
     "Virtual machine was not created within this VirtualBox instance."}
    (.registerMachine vbox machine)))

(defn ^IMachine create-machine
  ([vbox name os-type-id]
     (create-machine vbox name os-type-id false))
  ([vbox name os-type-id overwrite?]
     (create-machine vbox name os-type-id overwrite? nil))
  ([^IVirtualBox vbox ^String name os-type-id overwrite? ^String base-folder]
     {:pre [(model/IVirtualBox? vbox)]}
     (let [flags (if overwrite? "forceOverwrite=1" nil)
           path (when base-folder
                  ;; using no groups for now (group=nil)
                  (.composeMachineFilename vbox name "/vmfest" flags base-folder))]
       (conditions/with-vbox-exception-translation
         {:VBOX_E_OBJECT_NOT_FOUND "invalid os type ID."
          :VBOX_E_FILE_ERROR
          (str "Resulting settings file name is invalid or the settings"
               " file already exists or could not be created due to an"
               " I/O error.")
          :E_INVALIDARG "name is empty or null."}
         (log/infof
           "create-machine: Creating machine %s in %s, %s overwriting previous contents"
           name
           path
           (if overwrite? "" "not"))
         ;; using no groups for now (groups=nil)
         (.createMachine vbox path name ["/vmfest"] os-type-id flags)))))

(defn api-version [^IVirtualBox vbox]
  (.getAPIVersion vbox))

(defn dhcp-info [^IDHCPServer dhcp]
  {:ip-address (.getIPAddress dhcp)
   :enabled? (.getEnabled dhcp)
   :network-mask (.getNetworkMask dhcp)
   :network-name (.getNetworkName dhcp)
   :lower-ip (.getLowerIP dhcp)
   :upper-ip (.getUpperIP dhcp)})

(defn dhcp-infos [^IVirtualBox vb]
  (let [infos (map dhcp-info (.getDHCPServers vb))]
    (zipmap (map :network-name infos)
            (map #(dissoc % :network-name ) infos))))

(defn interface-info [^IHostNetworkInterface interface dhcps]
   (let [network-internal-name (.getNetworkName interface)
            dhcp-server (get dhcps network-internal-name )]
     {:network-name (.getName interface)
      :network-internal-name network-internal-name
      :dhcp-enabled? (or (.getDHCPEnabled interface)
                         (:enabled? dhcp-server))
      :dhcp-server dhcp-server
      :ip-address (.getIPAddress interface)
      :medium-type (enums/host-network-interface-medium-type-to-key
                    (.getMediumType interface))
      :status (enums/host-network-interface-status-to-key
               (.getStatus interface))
      :interface-type (enums/host-network-interface-type-to-key
                       (.getInterfaceType interface))}))

(defn interface-infos [^IHost host ^IVirtualBox vbox]
  (let [dhcps (dhcp-infos vbox)
        interfaces (map #(interface-info % dhcps) (.getNetworkInterfaces host))]
    (zipmap (map :network-name interfaces)
            (map #(dissoc % :network-name) interfaces))))

(defn processor-feature-data [^IHost host]
  (let [keys [:hw-virt-ex :pae :long-mode :nested-paging]]
    (zipmap keys
            (map #(.getProcessorFeature
                  host
                  (enums/key-to-processor-feature %))
                 keys))))

(defn host-info [^IHost host ^IVirtualBox vbox]
  {:processor-count (.getProcessorCount host)
   :memory-size (/ (.getMemorySize host) 1024)
   :memory-available-pct  (/ (* 100.0 (.getMemoryAvailable host))
                             (.getMemorySize host))
   :operating-system (.getOperatingSystem host)
   :os-version (.getOSVersion host)
   :processor-features (processor-feature-data host)
   :network-interfaces (interface-infos host vbox)})

(defn vbox-info [^IVirtualBox vbox]
  {:api-version (.getAPIVersion vbox)
   :protocol (if (xpcom?) :xpcom :ws)
   :package-type (.getPackageType vbox)
   :home-filder (.getHomeFolder vbox)
   :settings-file-path (.getSettingsFilePath vbox)
   :host (host-info (.getHost vbox) vbox)
   ;; :dhcp-servers (dhcp-infos vbox)
   :internal-networks (into [] (.getInternalNetworks vbox))})

;;; DHCP

(defn ^IDHCPServer find-dhcp-by-interface-name
  "Find a dhcp server by the interface name to which it is attached.

  NOTE: This is not very reliable, as the original function does not
  do what it says it does. This function finds the dhcp by the *dhcp*
  name instead of the interface name. The DHCP server is usually named
  with the pattern `HostInterfaceNetworking-vboxnetN`.

  This function, then, looks a DHCP named
  `HostNetworkInterfaceType-NAME` where NAME is the name of the host
  interface"

  [^IVirtualBox vbox interface-name]
  (try (.findDHCPServerByNetworkName
        vbox
        (str "HostInterfaceNetworking-" interface-name))
       (catch Exception e nil)))

(defn ^IDHCPServer create-dhcp-server [^IVirtualBox vbox interface-name]
  (conditions/with-vbox-exception-translation
    {:E_INVALIDARG "Host network interface name already exists."}
    (.createDHCPServer
     vbox
     (str "HostInterfaceNetworking-" interface-name))))
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
