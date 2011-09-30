(defproject vmfest "0.2.0-vbox40-SNAPSHOT"
  :description "Manage local VMs from the REPL"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojars.tbatchelli/vboxjws "4.0.2"]
                 [org.clojure/tools.logging "0.1.2"]
                 [slingshot "0.2.1"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [marginalia "0.5.0-alpha"]
                     [robert/hooke "1.1.0"]
                     [log4j/log4j "1.2.14"]
                     [lein-multi "1.0.0"]]
  :multi-deps {"1.2.0" [[org.clojure/clojure "1.2.0"]]
               "1.2.1" [[org.clojure/clojure "1.2.1"]]
               "1.3" [[org.clojure/clojure "1.3.0"]]}
  :test-selectors {:default (fn [v] (not (:integration v)))
                   :integration :integration
                   :all (fn [_] true)})
