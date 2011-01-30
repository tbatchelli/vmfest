(ns vmfest.virtualbox.session
  "**session** provides the functionality to abstract the creation and
destruction of sessions with the VBox servers"
  (:require [clojure.contrib.logging :as log]
        [vmfest.virtualbox.conditions :as conditions]
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox]
           [vmfest.virtualbox.model
            Server
            Machine]))

;; ## Low Level Functions

;; To interact with VBox server we need to create a web session. We do
;; this via IWebSessionManager. This does not create a connection to
;; the server yet, it just creates a data structure containing the
;; session details.

(defn ^IWebsessionManager create-session-manager
  "Creates a IWebsessionManager. Note that the default port is 18083

       create-session-manager: String
             -> IWebSessionManager"
   [url]
   (log/debug (str "Creating session manager for " url))
   (IWebsessionManager. url))

;; Before we can interact with the server we need to create a Virtual
;; Box object trhough our session, to keep track of our actions on the
;; server side.

(defn ^IVirtualBox create-vbox
  "Create a vbox object on the server represented by either the
IWebSessionManager object plus the credentials or by a Server object.

       create-vbox: IWebsessionManager x String x String 
       create-vbox: Session
             -> IVirtualBox"
  ([^IWebsessionManager mgr username password]
     (log/debug (str "creating new vbox with a logon for user " username))
     (try 
       (.logon mgr username password)
       (catch com.sun.xml.internal.ws.client.ClientTransportException e
         (conditions/log-and-raise
          e
          {:log-level :error
           :message (format
                     "Cannot connect to virtualbox server: '%s'"
                     (.getMessage e))}))))
  ([^Server server]
     (let [{:keys [url username password]} server
           mgr (create-session-manager url)]
       (create-vbox mgr username password))))

(defn create-mgr-vbox
  "A convenience function that will create both a session manager and
  a vbox at once

        create-mgr-vbox: Server 
        create-mgr-vbox: String x String x String
              -> [IWebSessionManager IVirtualBox]
"
  ([^Server server]
     (let [{:keys [url username password]} server]
       (create-mgr-vbox url username password)))
  ([url username password]
     (let [mgr (create-session-manager url)
           vbox (create-vbox mgr username password)]
       [mgr vbox])))

;; When interacting with the server it is important that the objects
;; created on the server side are cleaned up. Disconnecting the
;; session is the best way to ensure this clean up. The follwing macro
;; wraps a code block and creates a vbox with a session before the
;; code in the block is executed, and it will disconnect once the
;; block has been executed, thus cleaning up the session.

(defmacro with-vbox [^Server server [mgr vbox] & body]
  "Wraps a code block with the creation and the destruction of a session with a virtualbox.
       with-vbox: Server x [symbol symbol] x body
          -> body"
  `(let [[~mgr ~vbox] (create-mgr-vbox ~server)]
     (try
       ~@body
       (finally (when ~vbox
                  (try (.disconnect ~mgr ~vbox)
                       (catch Exception e#
                         (conditions/log-and-raise e# {:log-level :error
                                                       :message "unable to close session"}))))))))

;; When we want to manipulate the configuration of a VM, we need to
;; acquire a lock on such VM to prevent others from modifying it
;; concurrently. To do so we need to acquire a **direct session** with
;; the VM through the vbox hosting it.

(defmacro with-direct-session
  "Wraps the body with a session with a machine. "
  [machine [session vb-m] & body]
  `(try
     (let [machine-id# (:id ~machine)]
       (with-vbox (:server ~machine) [mgr# vbox#]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (.openSession vbox# ~session machine-id#)
           (log/trace (format "direct session is open for machine-id='%s'" machine-id#))
           (let [~vb-m (.getMachine ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (conditions/log-and-raise e#
                                           {:log-level :error
                                            :message
                                            (format "Called a method that is not available with a direct session in '%s'" '~body)
                                            :type :invalid-method})))))))
     (catch Exception e# 
       (conditions/log-and-raise e# {:log-level :error
                                     :message (format "Cannot open session with machine '%s' reason:%s"
                                                      (:id ~machine)
                                                      (.getMessage e#))}))))

(defmacro with-no-session
  [^Machine machine [vb-m] & body]
  `(try
     (with-vbox (:server ~machine) [_# vbox#]
       (let [~vb-m (model/soak ~machine vbox#)] 
         ~@body))
      (catch java.lang.IllegalArgumentException e#
        (conditions/log-and-raise e# {:log-level :error
                                      :message "Called a method that is not available without a session"
                                      :type :invalid-method}))
       (catch Exception e#
         (conditions/log-and-raise e# {:log-level :error
                                       :message "An error occurred"}))))

(defmacro with-remote-session
  [^Machine machine [session console] & body]
  `(try
     (let [machine-id# (:id ~machine)]
       (with-vbox (:server ~machine) [mgr# vbox#]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (log/info (format "with-remote-session: Opening existing session for machine %s" ~machine))
           (.openExistingSession vbox# ~session machine-id#)
           (log/trace (str "new remote session is open for machine-id=" machine-id#))
           (let [~console (.getConsole ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (conditions/log-and-raise e# {:log-level :error
                                               :message "Called a method that is not available without a session"
                                               :type :invalid-method})))))))
     (catch Exception e#
       (conditions/log-and-raise e# {:log-level :error
                                     :message "An error occurred"}))))


(comment
  ;; how to create the mgr and vbox independently
  (def mgr (create-session-manager "http://localhost:18083"))
  (def vbox (create-vbox mgr "" ""))

  ;; Creating Server and Machine to use with session
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (def vb-m (vmfest.virtualbox.model.Machine. machine-id server nil))
  
  ;; read config values with a session
  (with-direct-session vb-m [session machine]
    (.getMemorySize machine))

  ;; set config values
  (with-direct-session vb-m [session machine]
    (.setMemorySize machine (long 2048))
    (.saveSettings machine))

  ;; start a vm
  (require '[vmfest.virtualbox.machine :as machine])
  (machine/start vb-m)

    ;; stop a vm (or control it)
  (with-remote-session vb-m [session machine]
    (.powerDown machine))

  ;; read config values without a session
  (with-no-session vb-m [machine]
    (.getMemorySize machine))

)
