(ns vmfest.virtualbox.model)

(defrecord server [url username password]) 
(defrecord location [servers])
(defrecord machine [id server location])
(defrecord guest-os-type [id server])

(defprotocol vbox-object
  (as-map [this])
  (soak [this vbox]))

(defprotocol vbox-remote-object
  (dry [this server]))


