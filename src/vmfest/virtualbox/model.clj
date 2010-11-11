(ns vmfest.virtualbox.model
  (:import [com.sun.xml.ws.commons.virtualbox_3_2 IVirtualBox]))

(defrecord Server [url username password]) 
(defrecord Location [servers])
(defrecord Machine [id server location])
(defrecord GuestOsType [id server])

(defprotocol vbox-object
  (as-map [this])
  (soak [this ^IVirtualBox vbox]))

(defprotocol vbox-remote-object
  (dry [this server]))
