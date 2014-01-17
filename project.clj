(defproject vmfest "0.4.0-alpha.1"
  :description "Manage local VMs from the REPL"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [slingshot "0.10.3"]
                 [org.clojure/tools.logging "0.2.3"]
                 [fs "1.0.0"]
                 [org.apache.commons/commons-compress "1.4.1"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:1.5.1 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :ws {:dependencies [[org.clojars.tbatchelli/vboxjws "4.3.4"]]}
             :xpcom {:dependencies [[org.clojars.tbatchelli/vboxjxpcom "4.3.4"]]}
             :dev {:dependencies [[ch.qos.logback/logback-classic "1.0.1"]
                                  [ch.qos.logback/logback-core "1.0.1"]
                                  [org.clojure/tools.nrepl "0.2.3"]]}}
  :test-selectors {:default (fn [v] (not (:integration v)))
                   :integration :integration
                   :all (fn [_] true)}
  :jar-exclusions [#"log4j.xml"
                   #"logback.xml"]
  :jvm-opts ["-Dvbox.home=/Applications/VirtualBox.app/Contents/MacOS"])
