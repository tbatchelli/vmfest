(ns vmfest.virtualbox.session
  "**session** provides the functionality to abstract the creation and
destruction of sessions with the VBox servers"
  (:require [clojure.tools.logging :as log]
            [vmfest.virtualbox.conditions :as conditions]
            [vmfest.virtualbox.model :as model]
            [vmfest.virtualbox.enums :as enums])
  (:import [org.virtualbox_4_0
            VirtualBoxManager
            IVirtualBox
            VBoxException
            LockType
            ISession]
           [vmfest.virtualbox.model
            Server
            Machine]))

;; ## Session checks

(defn check-session-types
  [session-type requested-type]
  {:pre [(#{:write-lock :shared :remote :null} requested-type)
         (#{:write-lock :shared :remote :null} session-type)]}
  (condp =  [requested-type session-type]
      [:write-lock :write-lock] true
      [:write-lock :remote] true
      [:shared :write-lock] true
      [:shared :shared] true
      false))

(extend-type ISession
  model/Session
  (check-session
   [this required-type]
   (let [current-type (enums/session-type-to-key (.getType this))]
     (assert
      (check-session-types current-type required-type))
     true)))


;; ## Low Level Functions

;; To interact with VBox server we need to create a web session. We do
;; this via IWebSessionManager. This does not create a connection to
;; the server yet, it just creates a data structure containing the
;; session details.

(defn ^VirtualBoxManager create-session-manager
  "Creates a VirtualBoxManager. Home is where the vbox binaries are.
If no home is passed, it will use the default

       create-session-manager: (String)
             -> VirtualBoxManager"
  [& [home]]
   (log/trace (str "Creating session manager for home=" (or home "default")))
   (VirtualBoxManager/createInstance home))

;; Before we can interact with the server we need to create a Virtual
;; Box object trhough our session, to keep track of our actions on the
;; server side.

(defn ^IVirtualBox create-vbox
  "Create a vbox object on the server represented by either the
VirtualBoxManager object plus the credentials or by a Server object.

       create-vbox: VirtualBoxManager String x String x String
       create-vbox: Session
             -> IVirtualBox"
  ([^VirtualBoxManager mgr url username password]
     {:pre [(model/VirtualBoxManager? mgr)]}
     (log/tracef
      "creating new vbox with a logon for url=%s and username=%s" url username)
     (try
       (.connect mgr url username password)
       (.getVBox mgr)
       (catch VBoxException e
         (conditions/wrap-exception
          e
          {:message (format
                     "Cannot connect to virtualbox server: '%s'"
                     (.getMessage e))}))))
  ([^Server server]
     {:pre [(model/Server? server)]}
     (let [{:keys [url username password]} server
           mgr (create-session-manager)]
       (create-vbox mgr url username password))))

(defn create-mgr-vbox
  "A convenience function that will create both a session manager and
  a vbox at once

        create-mgr-vbox: Server
        create-mgr-vbox: String x String x String
              -> [VirtualBoxManager IVirtualBox]
"
  ([^Server server]
     {:pre [(model/Server? server)]}
     (let [{:keys [url username password]} server]
       (create-mgr-vbox url username password)))
  ([url username password]
     (let [mgr (create-session-manager )
           vbox (create-vbox mgr url username password)]
       [mgr vbox])))

;; When interacting with the server it is important that the objects
;; created on the server side are cleaned up. Disconnecting the
;; session is the best way to ensure this clean up. The follwing macro
;; wraps a code block and creates a vbox with a session before the
;; code in the block is executed, and it will disconnect once the
;; block has been executed, thus cleaning up the session.

(defmacro with-vbox [^Server server [mgr vbox] & body]
  "Wraps a code block with the creation and the destruction of a session
with a virtualbox.
       with-vbox: Server x [symbol symbol] x body
          -> body"
  {:pre [(model/VirtualBoxManager? server)]}
  `(let [[~mgr ~vbox] (create-mgr-vbox ~server)]
     (try
       ~@body
       (finally (when ~mgr
                  (try (.disconnect ~mgr)
                       (catch Exception e#
                         (conditions/wrap-exception
                          e# {:message "unable to close session"}))))))))

(def lock-type-constant
  {:write org.virtualbox_4_0.LockType/Write
   :shared org.virtualbox_4_0.LockType/Shared})

(defmacro with-session
  [machine type [session vb-m] & body]
  #_{:pre [(model/Machine? machine)]}
  `(try
     (with-vbox (:server ~machine) [mgr# vbox#]
       (let [~session (.getSessionObject mgr#)
             immutable-vb-m# (.findMachine vbox# (:id ~machine))]
         (.lockMachine immutable-vb-m# ~session (~type lock-type-constant))
         (let [~vb-m (.getMachine ~session)]
           (try
             ~@body
             (finally (.unlockMachine ~session))))))
     (catch VBoxException e#
       (conditions/wrap-exception
        e#
        {:message (format "Cannot open session with machine '%s' reason:%s"
                          (:id ~machine)
                          (.getMessage e#))}))))


(defmacro with-no-session
  [^Machine machine [vb-m] & body]
  #_{:pre [(model/Machine? machine)]}
  `(try
     (with-vbox (:server ~machine) [_# vbox#]
       (let [~vb-m (.findMachine vbox# (:id ~machine))]
         ~@body))
       (catch Exception e#
         (conditions/wrap-exception e# {:log-level :error
                                       :message "An error occurred"}))))



(comment
  ;; how to create the mgr and vbox independently
  (def mgr (create-session-manager))
  (def vbox (create-vbox mgr "http://localhost:18083" "" ""))

  ;; Creating Server and Machine to use with session
  (def server (vmfest.virtualbox.model.Server. "http://localhost:18083" "" ""))
  (def vb-m (vmfest.virtualbox.model.Machine. machine-id server nil))

  ;; read config values with a session
  (with-session vb-m :write [session machine]
    (.getMemorySize machine))

  ;; set config values
  (with-session vb-m :direct [session machine]
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
