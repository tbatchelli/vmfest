(defproject vmfest "0.2.3-SNAPSHOT"
  :description "Manage local VMs from the REPL"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/tools.logging "0.1.2"]
                 [org.clojars.tbatchelli/vboxjws "4.0.2"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.3.1"]
                     [marginalia "0.5.0-alpha"]
                     [robert/hooke "1.1.0"]
                     [log4j/log4j "1.2.14"]]
  :test-selectors {:default (fn [v] (not (:integration v)))
                   :integration :integration
                   :all (fn [_] true)}
  :jar-exclusions [#"log4j.xml"])
