(ns vmfest.vmfest
  (:require vmfest.virtualbox.model
             vmfest.virtualbox.machine
             vmfest.virtualbox.guest-os-type))

(comment
  (use 'vmfest.vmfest)
  (use '[vmfest.virtualbox.virtualbox :as vbox])
  (def my-machine (vbox/find-machine "http://localhost:18083" "" "" "CentOS Minimal"))
  (use 'vmfest.virtualbox.model)
  (as-map my-machine)
  (use '[vmfest.virtualbox.machine :as machine])
  (machine/start my-machine)
  (machine/stop my-machine)
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (vbox/machines server)
  (use '[vmfest.virtualbox.session :as session])
  (session/with-no-session my-machine [machine]
    (.getMemorySize machine))
  (session/with-direct-session my-machine [session machine]
    (.setMemorySize machine (long 1024))
    (.saveSettings machine)))
  

