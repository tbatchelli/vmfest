(defproject vmfest "0.2.4-SNAPSHOT"
  :description "Manage local VMs from the REPL"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/tools.logging "0.1.2"]
                 [org.clojars.tbatchelli/vboxjws "4.0.2"]]
  :dev-dependencies [[swank-clj "0.1.6"]
                     [marginalia "0.5.0-alpha"]
                     [robert/hooke "1.1.0"]
                     [log4j/log4j "1.2.14"]
                     [lein-clojars "0.6.0"]]
  :test-selectors {:default (fn [v] (not (:integration v)))
                   :integration :integration
                   :all (fn [_] true)}
  :jar-exclusions [#"log4j.xml"])
