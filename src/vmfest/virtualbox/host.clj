(ns vmfest.virtualbox.host
  (:import [org.virtualbox_4_3
            HostNetworkInterfaceType Holder IHost IHostNetworkInterface
            IVirtualBox IDHCPServer])
  (:require [vmfest.virtualbox.conditions :as conditions]
            [clojure.tools.logging :as log])
  (:use [vmfest.virtualbox.virtualbox :only [find-dhcp-by-interface-name
                                             create-dhcp-server]]))

(defn- ^IHostNetworkInterface add-one-host-only-interface
  "Creates the next host-only interface in the host"
  [^IHost host]
  ;; There is no way (that I could find) to create an interface
  ;; object, but I found this trick by inspecting the source code of
  ;; the java API.
  ;;
  ;; A `Holder` is just a objec that holds a variable with the
  ;; intention to be used as a R/W parameter to functions. I assume
  ;; all this crap is need for SOAP purposes.
  ;;
  ;; What we're interested is in the contents (`value`) of this
  ;; `holder`
  (let [iface (IHostNetworkInterface/queryInterface host)
        interface-holder (Holder. iface)]
    (.waitForCompletion
     (.createHostOnlyNetworkInterface host interface-holder) (Integer. -1))
    (let [interface (.value interface-holder)]
      (log/infof "Created Host Only network interface %s"
                 (.getName interface))
      interface)))

(defn- add-host-only-interface*
  "Adds new hosts interfaces until the host interface with `if-name` is created.

  Returns a vector with the interface just created (`IHostOnlyNetworkInterface`)
  and a vector of the UUIDs of the temporary interfaces coreated.

  CAUTION: This method assumes that `if-name` is of the form
  `vboxnetN`. If this is not the case, it will create interfaces
  forever"
  [host if-name temp-ifs]
  (let [interface (add-one-host-only-interface host)]
    (if (= if-name (.getName interface))
      [interface temp-ifs]
      (do
        (log/warnf "Scheduling host-only interface %s for deletion"
                   (.getName interface))
        (recur host if-name (conj temp-ifs interface))))))

(defn remove-host-only-interface [^IHost host if-uuid]
  (conditions/with-vbox-exception-translation
    {:VBOX_E_OBJECT_NOT_FOUND "No host network interface matching id found"}
    (.removeHostOnlyNetworkInterface host if-uuid)))

(defn- ip-vec-to-string [[a b c d]]
  (format "%s.%s.%s.%s" a b c d))

(defn- ip-string-to-vec [ip]
  (vec (map #(Integer/parseInt %)
            (clojure.string/split ip #"\."))))

(defn configure-dhcp
  "Configures a DHCP server

  server: a live IDHCPServer
  server-ip: the IP addres to which the DHCP server will bind
  network-mask: the network mask for the sever
  from-ip: first IP address controlled by this server
  to-ip: last IP address controlled by this server

  All ip addresses are verctors of 4 numbers e.g. 192.168.0.1 -> [192 168 0 1]
  "
  [^IDHCPServer server server-ip netmask from-ip to-ip]
  (let [[^String server-ip ^String netmask ^String from-ip ^String to-ip]
        (map ip-vec-to-string [server-ip netmask from-ip to-ip])]
    (log/infof
     "Configuring DHCP server %s with ip %s mask %s from %s to %s"
     (.getNetworkName server) server-ip netmask from-ip to-ip)
    (conditions/with-vbox-exception-translation
      {:E_INVALIDARG "invalid configuration supplied" }
      (.setConfiguration server server-ip netmask from-ip to-ip))))

(defn- ensure-dhcp-with-defaults [vbox host ^IHostNetworkInterface interface]
  (let [if-ip (ip-string-to-vec (.getIPAddress interface))
        if-name (.getName interface)
        base-ip (subvec if-ip 0 3)
        server-ip (conj base-ip 100)
        netmask [255 255 255 0]
        from-ip (conj base-ip 101)
        to-ip (conj base-ip 254)
        dhcp-server (or (find-dhcp-by-interface-name vbox if-name)
                        (create-dhcp-server vbox if-name))]
    (log/debugf "Ensuring the interface %s has a configured DHCP" if-name)
    (if (and
         dhcp-server
         (.getEnabled dhcp-server)
         (not (= "0.0.0.0" (.getIPAddress dhcp-server))))
      (log/infof "DHCP for interface %s seems to be configured." if-name)
      (do
        ;; either there is no dhcp server, or it is not enabled or it is
        ;; not configured
        (log/warnf
         (str "Configure DHCP server for interface %s with server-ip %s "
              "netmask %s from-ip %s to-ip %s")
         if-name server-ip netmask from-ip to-ip)
        (configure-dhcp dhcp-server server-ip netmask from-ip to-ip)
        (.setEnabled dhcp-server true)
        ;; NOTE: I have no idea what the two last parameters are, which
        ;; are `trunkName` and `trunkType`, so I am passing a `nil`,
        ;; which seems to do the trick.
        (.start dhcp-server if-name nil nil)))))

(defn add-host-only-interface [^IVirtualBox vbox if-name]
  ;; For some reason the vbox API does not let you set the name of
  ;; the network interface and instead it creates interfaces named
  ;; "vboxnetN", where N is the name lowest number for which an
  ;; interface vboxnetN does not exist.
  ;;
  ;; e.g. So let's say we have "vboxnet0" and "vboxnet1", then the
  ;; next interface would be "vboxnet2"
  ;;
  ;; e.g. Let's say we have "vboxnet0" and "vboxnet2", then the next
  ;; interface would be "vboxnet1"
  ;;
  ;; What this means is that in order to create vboxnet5, we need to
  ;; first ensure that vboxnet[0-4] exist.
  ;;
  ;; We can create the missing interfaces, but then we need to clean
  ;; after ourselves...
  (let [host (.getHost vbox)]
    (log/warn "Creating a new Host Only network interface")
    (let [[interface ifs-to-delete] (add-host-only-interface* host if-name [])]
      ;; delete temporarily created interfaces
      (doseq [^IHostNetworkInterface if-to-delete ifs-to-delete]
        (log/warnf "Executing scheduled deletion of interface %s"
                   (.getName if-to-delete))
        (remove-host-only-interface host (.getId if-to-delete)))
      (ensure-dhcp-with-defaults vbox host interface))))
