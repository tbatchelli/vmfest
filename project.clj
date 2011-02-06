(defproject vmfest "0.1.0"
  :description "Manage local VMs from the REPL"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojars.tbatchelli/vboxjws "4.0.2"]
                 [log4j/log4j "1.2.14"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [marginalia "0.5.0-alpha"]
                     [robert/hooke "1.1.0"]]
  :test-selectors {:default (fn [v] (not (:integration v)))
                   :integration :integration
                   :all (fn [_] true)})
