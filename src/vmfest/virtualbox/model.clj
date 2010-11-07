(ns vmfest.virtualbox.model)

(defrecord server [url username password]) 
(defrecord location [servers])
(defrecord machine [uuid server location])

