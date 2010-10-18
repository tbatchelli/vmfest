(ns vmfest.virtualbox.vbox
  (:use vmfest.machine
        clojure.contrib.logging)
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox
            ISession
            IWebsessionManager]))

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
;; objects) in a read-only way. For this one doesn't need 'access' to
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
;; machine at the server side. This is done via agents. A machine is
;; partially represented by an agent and any attempt at updating a
;; machine must be done by means of a function passed to this agent.
;;
;; ENSURING PERFORMACE
;;
;; In order to allow this module access to more than one machine at a
;; time, each machine will have its own client, and any access to this
;; machine will have to take place via this client. 

(defn- ^IWebsessionManager create-session-manager
  "Creates a IWebsessionManager. Note that the default port is 18083"
  [host port]
  (let [url (str "http://" host ":" port)]
    (debug (str "Creating session manager for " url))
    (IWebsessionManager. url)))

(defn- ^IVirtualBox create-vbox
  "Creates a VirtualBox by logging in through the IWebsessionManager
using 'username' and 'password' as credentials"
  [^IWebsessionManager mgr username password]
  (debug (str "creating new vbox with a logon for user " username))
  (.logon mgr username password))

(defn- find-machine
  "Finds where a machine exists from either its ID or its name. Returns
the IMachine corresponding to the supplied name or ID, or null if such
machine cannot be found.

vbox - A VirtualBox
vm-string A String with either the ID or the name of the machine to find"
  [vbox vm-string]
  (try (.getMachine vbox vm-string)
       (catch Exception e
         (try (.findMachine vbox vm-string)
              (catch Exception e nil)))))


(defn- vbox-machine-in-valid-state?
  "Tests whether a virtualbox-machine is in valid state. It does that by
trying to obtain an atribute from the machine."
  [vb-m]
  (try (.getState @(:session-atom vb-m))
       true
       (catch Exception e false)))

(defn- reset-vbox-machine
  "Recreates the necessary parts of the virtualbox-machine to return it to
a good state, if possible, returning the machine itself. If it fails it will
return a null."
  [vb-m]
  ;; first try to reset the vbox, and then try with the session object. 
  (try (let [vbox (create-vbox (:mgr vb-m) (:username vb-m) (:password vb-m))]
         (swap! (:vbox-atom vb-m) (fn [_] vbox)))
       (try (let [session (.getSessionObject (:mgr vb-m) @(:vbox-atom vb-m))]
              (swap! (:session-atom vb-m) (fn [_] session)))
            (catch Exception e
              (error (str "creating obtaining the session from " (:url vb-m)
                          " : " (.getMessage e)))))
       vb-m ;; all is good. The reset was successful.
       (catch Exception e
         (error (str "creating a virtualbox session with " (:url vb-m)
                     " : " (.getMessage e))))))


(defn refresh-machine
  "Checks if the machine is still in good standing with the server counterpart
i.e. that the server-objects exist, that the connection is possible, etc...
If it is not the case, it tries to reconnect. Will return false if the machine is
in good standing or it was correctly reconnected, and nil otherwise"
  [vb-m]
  (let [^ISession session @(:session-atom vb-m)]
    ;; try to access the machine, if it works, return the true.
    (try (.getState session)
         true ;; the connection to the machine is good and has been refreshed
         (catch com.sun.xml.internal.ws.client.ClientTransportException e
           ;; communication isssue. Giving up
           (error (str "Can't connect to the virtualbox at http://"
                       (:url vb-m) " with user " (:username vb-m))))
         (catch javax.xml.ws.WebServiceException e
           ;; this could mean anything. Let's be more specific
           (let [cause (.getCause e)]
             (if (= (class cause)  org.virtualbox_3_2.InvalidObjectFaultMsg)
               ;; either the managed objects have been garbage
               ;; collected or the server has been restarted. Try
               ;; recreating the connection.
               (do (warn "The machine is no longer valid, will attempt to recreate it")
                   (reset-vbox-machine vb-m)
                   (if (vbox-machine-in-valid-state? vb-m)
                     @(:session-atom vb-m)
                     ;; recreating didn't work. It must be something
                     ;; more serious. Giving up.
                     (error "the machine is not valid and can't be recreated")))
               ;; the error wasn't recoverable after all. giving up.
               (error "The machine is not valid" e)))))))

;; the session might be stale. We before returning it to the user we
;; check it by queirying its status. We only really care if an
;; exception is thrown, and make sure the exception is recoverable.
(defn- get-session
  "Tries to safely obtain the ISession of a machine. If the machine is stale
then it will try to reset it to a fresh state. Will return nil if it fails to
return a good session."
  [vb-m]
  (when (refresh-machine vb-m)
    @(:session-atom vb-m)))

(defn- get-vbox
  "Tries to safely obtain the IVirtualBox of a machine. If the machine is stale
then it will try to reset it to a fresh state. Will return nil if it fails to
return a good VirtualBox"
  [vb-m]
  (when (refresh-machine vb-m)
    @(:vbox-atom vb-m)))

(defn- vbox-task
  "Wraps a function to be executed on a machine so that it can be sent to the
machine agent. The task-fn function must take ISession as its single parameter"
  [vbox-machine task-fn] ;; task-fn must take ISession as first parameter
  (fn [machine-agent]
    (with-open [^ISession session (get-session vbox-machine)]  ; might force a refresh
      (let [^IVirtualBox vbox (get-vbox vbox-machine)
            machine-id (:machine-id vbox-machine)] 
        (try (if (nil? session)
               (error (str "Couldn't create session for machine-id:" machine-id))
               (do
                 (debug (str "opening session for machine-id:" machine-id))
                 (.openSession vbox session machine-id)
                 (task-fn session)))
             (catch Exception e
               (error "ERROR" e))
             (finally ;; always make sure the session is closed!
              (debug (str "closing session for machine-id:" machine-id)))))))) 

(defrecord vbox-machine
  [;; the URL to connect to the VB server for this machine
   url                         
   ;; IWebsessionManager used to connect to the machine
   ^IWebsessionManager mgr 
   ;; An atom holding the IVirtualBox object that contains the
   ;; machine.  NOTE: do not access this directly, use (get-vbox)
   vbox-atom
   ;; An atom holding the ISession object corresponding to the
   ;; VirtualBox.  NOTE: do not access this field direclty, use
   ;; (get-session)
   session-atom   
   ;; The username used to log into this VirtualBox
   username
   ;; The password used to log into this VirtualBox
   password
   ;; the ID of the machine (UUID)
   machine-id                
   ;; An agent whose sole purpose is serialize access to this machine
   serializer-agent
   ]         
  machine ;; the protocol it implements
  ;; Wraps the task-fn function and sends it to the agent to be
  ;; executed. This guarantees serialized access to this machine.
  (execute-task
   [this task-fn]
                (let [task (vbox-task this task-fn)]
                  (send serializer-agent task))))

(defn build-vbox-machine [hostname port username password machine-name-or-id]
  (let [mgr (create-session-manager hostname port)
        vbox-atom (atom (create-vbox mgr username password))
        session-atom (atom (.getSessionObject mgr @vbox-atom))
        machine-id (.getId (find-machine @vbox-atom machine-name-or-id))
        serializer-agent (agent nil)]
    (if (or (nil? machine-id)
            (nil? @session-atom)
            (nil? @vbox-atom))
      nil
      (vbox-machine. (str "http://" hostname ":" port)
                     mgr vbox-atom session-atom
                     username password machine-id serializer-agent))))



(comment
  (defn demo-set-memory-task [n-megas]
    (fn [session]
      (let [mutable-machine (.getMachine session)]
        (println  (str"setting the memory to" n-megas "for machine-id" (.getId mutable-machine)))
        (.setMemorySize mutable-machine (long n-megas))
        (.saveSettings mutable-machine))))
  (use 'vmfest.virtualbox.vbox)
  (def my-machine (build-vbox-machine "localhost" "18083" "test" "ttest" "CentOS Minimal"))
  (execute-task my-machine (demo-set-memory-task 1024))
  ;; restart vboxwebsrv
  (execute-task my-machine (demo-set-memory-task 768)) ; observe it still works
  )

