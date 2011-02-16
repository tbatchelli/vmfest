(defproject vmfest "0.1.1"
  :description "Manage local VMs from the REPL"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojars.tbatchelli/vboxws_java16 "3.2.10"]
                 [log4j/log4j "1.2.14"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]
                     [marginalia "0.3.2"]])
