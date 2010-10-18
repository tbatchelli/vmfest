(ns vmfest.machine)

(defprotocol machine
 ; (initialize [s])
  (execute-task [s task])
 ; (destroy [s])
 ; (get-id [s])
  )