(ns vmfest.virtualbox.vbox
  (:use clojure.contrib.logging
        [vmfest.util :as util]))

;;; --------- this text is outdated. It is here for reference only...

;;; README
;; Connecting to VirtualBox via the java.ws interfaces has a series of
;; challenges. this code reflects these challenges and tries to hide
;; them from the module user.
;;
;; 1) The VB api is stateful. In fact, for each object created at the
;; client side there is a counterpart object created at the server
;; side.
;;
;; 2) There is no guarantee that at any time, for all the objects in
;; the client side there will exists their server-side
;; counterpart. The serverside objects can be reclaimed on timout
;; Notice that this timeout is a configurable setting for vboxwebsrv
;; (-t) and can be turned off completely (-t 0). Also, if vboxwebsrv
;; could be restarted at any time and all the couunterpart objects to
;; the objects in the client would be destroyed.
;;
;; 3) Each client of a VM can only access one machine at a time. Also,
;; each machine can only be accessed by one client at a time. The
;; limit of only allowing access to one machine at a time would mean
;; that this module can only interactue with machines one at a time,
;; potentially taking too long if the number of machines to be
;; accessed is large. There is a little bit of hope: if you create
;; many clients, those clients can access one machine concurrently
;; with other clients accessing other machines.
;;
;; HOW TO HAVE WRITE ACCESS TO A MACHINE
;;
;; There are means to access data about a machine (or other API
;; objects) in a read-only way. For this one doesn't need 'access' toy
;; that machine.
;;
;; If a client wants to make changes to a machine, the client needs to
;; obtain access to it first, make the changes, save them and release
;; the obtained access so that others can obtain it and access the
;; machine afterwards.
;;
;; In order to obtain access to a machine, a system needs to create a
;; connection to the VM server via an IWebsessionManager object. From
;; this object, the system can create multiple clients by loging on
;; via this IWebsessionManager. Each client is a IVirtualBox. When a
;; IVirtualBox is created, a ISession object is created and attached
;; to it. This ISession object is the one that ensures serial access
;; to the VMs. In order to obtain access to a machine the client must
;; obtain it via its ISession object.
;;
;; ENSURING STABILITY
;;
;; To ensure stability of this VirtualBox client, this module has
;; mechanisms in place to overcome the issues created by 1) and
;; 2). Namely:
;;
;; a) Before utiliing any ISession or IVirtualBox object, the system
;; will test if the connection is still available and whether the
;; server counterparts still exist. In case one of the checks fails,
;; the system will re-create the objects ensuring that they are in
;; good standing before being used.
;;
;; b) In order for a) to happen, the system stores the information
;; necessary to re-create these objects
;;
;; ENSURING CORRECTNESS
;;
;; Access to each machine will be guarded by this library in order to
;; ensure that there are no failed attempts at obtaining access to the
;; machine at the server side. This is done via a monitor. Access to
;; the machine is should be done only via execute-task to ensure that
;; the proper lock is acquired.
;;
;; ENSURING PERFORMACE
;;
;; In order to allow this module access to more than one machine at a
;; time, each machine will have its own client, and any access to this
;; machine will have to take place via this client. 

;; {:attribute function-to-set-the-attribute}
;; contains all the settable attributes in IMachine with their setter functions
(defonce *machine-settable-attributes*
  (util/settable-attribute-method-map IMachine))

;; {:attribute function-to-set-the-attribute}
;; contains all the gettable attributes in IMachine with their setter functions
(defonce *machine-gettable-attributes*
  (util/gettable-attribute-method-map IMachine))

(defn set-attributes
  "expecting {:attribute-name [val1 val2 ... valN]}, sets on the
object all the attributes in the map passing the values as parameters
to the setter"
  [attribute-values-map object]
  (let [set-attribute
        (fn [[attribute values]]
          (let [method-fn (attribute *machine-settable-attributes*)]
            (trace (str "set " attribute " = " values))
            (apply method-fn object values)))]
    (doall (map set-attribute attribute-values-map))))

(defn get-attributes
  "expecting [:attribute-name], sets on the object all the attributes
in the map passing the values as parameters to the setter"
  [attribute-keys-vector object]
  (let [get-attribute
        (fn [key]
          (let [method-fn (key *machine-gettable-attributes*)
                value (method-fn object)]
            (trace (str "get " key " = " value))
            [key value]))]
    (doall (into {} (map get-attribute attribute-keys-vector)))))


(comment
  ;; setting a bunch of attributes
  (execute-task my-machine
                (set-attributes-task
                 {:memory-size [(long 1024)]
                  :cpu-count [(long 2)]
                  :name ["A new name"]}))
  ;; getting a bunch of attributes
  (execute-task my-machine
                (get-attributes-task
                 [:memory-size
                  :cpu-count 
                  :name ]))
  ;; --> {:memory-size 1024, :cpu-count 1, :name "CentOS Minimal"}
  )
