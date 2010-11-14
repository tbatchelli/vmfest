(ns vmfest.virtualbox.session
  (:require [clojure.contrib.logging :as log]
        [vmfest.virtualbox.conditions :as conditions]
        [vmfest.virtualbox.model :as model])
  (:import [com.sun.xml.ws.commons.virtualbox_3_2
            IWebsessionManager
            IVirtualBox]
           [vmfest.virtualbox.model
            Server
            Machine]))

(defn- ^IWebsessionManager create-session-manager
   "Creates a IWebsessionManager. Note that the default port is 18083"
   [url]
   (log/debug (str "Creating session manager for " url))
   (IWebsessionManager. url))

(defn- ^IVirtualBox create-vbox
 "TODO"
 ([^IWebsessionManager mgr username password]
    (log/debug (str "creating new vbox with a logon for user " username))
    (try 
      (.logon mgr username password)
      (catch com.sun.xml.internal.ws.client.ClientTransportException e
        (conditions/log-and-raise e :error
                       (format "Cannot connect to virtualbox server: '%s'" (.getMessage e))))))
 ([^Server server]
    (let [{:keys [url username password]} server
          mgr (create-session-manager url)]
      (create-vbox mgr username password))))

(defn create-mgr-vbox
  ([^Server server]
     (let [{:keys [url username password]} server]
       (create-mgr-vbox url username password)))
  ([url username password]
     (let [mgr (create-session-manager url)
           vbox (create-vbox mgr username password)]
       [mgr vbox])))

(defmacro with-vbox [^Server server [mgr vbox] & body]
  `(let [[~mgr ~vbox] (create-mgr-vbox ~server)]
     (try
       ~@body
       (finally (when ~vbox
                  (try (.logoff ~mgr ~vbox)
                       (catch Exception e#
                         (conditions/log-and-raise e# :error "unable to close session"))))))))

(defmacro with-direct-session
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
                 (conditions/log-and-raise e# :error
                                (format "Called a method that is not available with a direct session in '%s'" '~body)
                                :type :invalid-method)))))))
     (catch Exception e# 
       (conditions/log-and-raise e# :error
                      (format "Cannot open session with machine '%s' reason:%s"
                              (:id ~machine)
                              (.getMessage e#))))))

(defmacro with-no-session
  [^Machine machine [vb-m] & body]
  `(try
     (with-vbox (:server ~machine) [_# vbox#]
       (let [~vb-m (model/soak ~machine vbox#)] 
         ~@body))
      (catch java.lang.IllegalArgumentException e#
         (conditions/log-and-raise e# :error "Called a method that is not available without a session"
                        :type :invalid-method))
       (catch Exception e#
         (conditions/log-and-raise e# :error "An error occurred"))))

(defmacro with-remote-session
  [^Machine machine [session console] & body]
  `(try
     (let [machine-id# (:id ~machine)]
       (with-vbox (:server ~machine) [mgr# vbox#]
         (with-open [~session (.getSessionObject mgr# vbox#)]
           (.openExistingSession vbox# ~session machine-id#)
           (log/trace (str "new remote session is open for machine-id=" machine-id#))
           (let [~console (.getConsole ~session)]
             (try
               ~@body
               (catch java.lang.IllegalArgumentException e#
                 (conditions/log-and-raise e# :error "Called a method that is not available without a session"
                                :type :invalid-method)))))))
     (catch Exception e#
       (conditions/log-and-raise e# :error "An error occurred"))))


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
