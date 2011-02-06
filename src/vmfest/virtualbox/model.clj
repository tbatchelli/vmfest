(ns vmfest.virtualbox.model
  (:import [org.virtualbox_4_0 IVirtualBox]))

(defrecord Server [url username password]) 
(defrecord Location [servers])
(defrecord Machine [id server location])
(defrecord GuestOsType [id server])
(defrecord HardDisk [id server])

(defprotocol vbox-object
  (as-map [this])
  (soak [this ^IVirtualBox vbox]))

(defprotocol vbox-remote-object
  (dry [this server]))
