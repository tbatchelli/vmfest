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
 ([^IWebsessionManager mgr username password]
     (debug (str "creating new vbox with a logon for user " username))
     (.logon mgr username password))
 ([^vbox-machine vb-m]
    (let [{:keys [url username password]} vb-m
          mgr (create-session-manager url)]
      (create-vbox mgr username password))))

(defn create-mgr-vbox [vb-m]
  (let [{:keys [url username password]} vb-m
        mgr (create-session-manager url)
        vbox (create-vbox mgr username password)]
    [mgr vbox]))

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
  (let [[session machine] names]
    `(try
       (let [machine-id# (:machine-id ~vb-m)
             [mgr# vbox#] (create-mgr-vbox ~vb-m)]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (.openSession vbox# ~session machine-id#)
           (trace (str "direct session is open for machine-id=" machine-id#))
           (let [~machine (.getMachine ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (error "Called a method that is not available with a direct session"))))))
       (catch Exception e#
         (error "Something happened" e#)))))

(defmacro with-no-session
  [vb-m names & body]
  (let [machine (first names)]
    `(try
       (let [[mgr# vbox#] (create-mgr-vbox ~vb-m)
             ~machine (find-machine vbox# (:machine-id ~vb-m))]
         ~@body)
       (catch java.lang.IllegalArgumentException e#
         (error "Called a method that is not available without a session"))
       (catch Exception e#
         (error "An error occurred")))))

(defmacro with-remote-session
  [vb-m names & body]
  (let [[session console] names]
    `(try
       (let [machine-id# (:machine-id ~vb-m)
             [mgr# vbox#] (create-mgr-vbox ~vb-m)]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (.openExistingSession vbox# ~session machine-id#)
           (trace (str "new remote session is open for machine-id=" machine-id#))
           (let [~console (.getConsole ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (error "Called a method that is not available with a remote session"))))))
       (catch Exception e#
         (error "Something happened" e#)))))

(defn start
  [vb-m & {:keys [session-type env]}]
  (let [machine-id (:machine-id vb-m)
        [mgr vbox] (create-mgr-vbox vb-m)
        session (.getSessionObject mgr vbox)
        session-type (or session-type "gui")
        env (or env "DISPLAY:0.0")]
    (try (let [progress (.openRemoteSession vbox session machine-id session-type env)]
           (debug (str "Starting session for VM " machine-id "..."))
           (.waitForCompletion progress 10000)
           (let [result-code (.getResultCode progress)]
             (if (zero? result-code)
               nil
               true))))))

;;;;;;;

(comment
  ;; this is only necessary to obtain the machine-id for the target virtual machine
  (def mgr (create-session-manager "http://localhost:18083"))
  (def vbox (create-vbox mgr "" ""))
  (def machine-id (.getId (find-machine vbox "CentOS Minimal")))

  ;; this is the only code actually needed to configure the virtual machine
  (def vb-m (build-vbox-machine "http://localhost:18083" "" "" machine-id))

  ;; read config values with a session
  (with-direct-session vb-m [session machine]
    (.getMemorySize machine))

  ;; set config values
  (with-direct-session vb-m [session machine]
    (.setMemorySize machine (long 2048))
    (.saveSettings machine))

  ;; start a vm
  (start vb-m)

  ;; stop a vm (or control it)
  (with-remote-session vb-m [session machine]
    (.powerDown machine))

  ;; read config values without a session
  (with-no-session vb-m [machine]
    (.getMemorySize machine)))
  