(ns vmfest.virtualbox.model
  (:import [org.virtualbox_4_0 IVirtualBox IMachine
            ISession VirtualBoxManager IMedium IConsole]))

(defrecord Server [url username password])
(defrecord Location [servers])
(defrecord Machine [id server location])
(defrecord GuestOsType [id server])
(defrecord HardDisk [id server])

(defprotocol vbox-object
  (as-map [this])
  (soak [this vbox]))

(defprotocol vbox-remote-object
  (dry [this server]))

(defprotocol Session
  (check-session [this type]))

(defn IMachine? [o]
  (instance? IMachine o))
(defn IVirtualBox? [o]
  (instance? IVirtualBox o))
(defn ISession? [o]
  (instance? ISession o))
(defn VirtualBoxManager? [o]
  (instance? VirtualBoxManager o))
(defn IMedium? [o]
  (instance? IMedium o))
(defn IConsole? [o]
  (instance? IConsole o))
(defn Server? [o]
  (instance? Server o))
(defn Machine? [o]
  (instance? Machine o))
