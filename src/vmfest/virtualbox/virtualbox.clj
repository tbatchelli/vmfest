(ns vmfest.virtualbox.virtualbox
  (:use clojure.contrib.logging)
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox]))

(defrecord vbox-machine
  [url
   username
   password
   machine-id])

(defn build-vbox-machine [url username password machine-id]
  (vbox-machine. url username password machine-id))

(defn ^IWebsessionManager create-session-manager
  "Creates a IWebsessionManager. Note that the default port is 18083"
  [url]
  (debug (str "Creating session manager for " url))
  (IWebsessionManager. url))

(defn ^IVirtualBox create-vbox
  "Creates a VirtualBox by logging in through the IWebsessionManager
using 'username' and 'password' as credentials"
  [^IWebsessionManager mgr username password]
  (debug (str "creating new vbox with a logon for user " username))
  (.logon mgr username password))

(defn find-machine
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

(defmacro with-direct-session
  [vb-m names & body]
  (let [session (first names)
        machine (second names)]
    `(let [username# (:username ~vb-m)
           password# (:password ~vb-m)
           machine-id# (:machine-id ~vb-m)
           url# (:url ~vb-m)]
       (try
         (let [mgr# (create-session-manager url#)
               vbox# (create-vbox mgr# username# password#)]
           (with-open [~session (.getSessionObject mgr# vbox#)]
             (.openSession vbox# ~session machine-id#)
             (trace (str "direct session is open for machine-id=" machine-id#))
             (let [~machine (.getMachine ~session)]
               (try
                 ~@body
                 (catch java.lang.IllegalArgumentException e#
                   (error "Called a method that is not available with a direct session"))))))
         (catch Exception e#
           (error "Something happened" e#))))))

;;;;;;;

(comment
  ;; this is only necessary to obtain the machine-id for the target virtual machine
  (def mgr (create-session-manager "http://localhost:18083"))
  (def vbox (create-vbox mgr "" ""))
  (def machine-id (.getId (find-machine vbox "CentOS Minimal")))

  ;; this is the only code actually needed to configure the virtual machine
  (def vb-m (build-vbox-machine "http://localhost:18083" "" "" machine-id))
  (with-direct-session vb-m [session machine] (.getMemorySize machine))
  (with-direct-session vb-m [session machine]
    (.setMemorySize machine (long 2048))
    (.saveSettings machine)))
  