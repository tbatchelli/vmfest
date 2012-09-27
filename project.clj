(defproject vmfest "0.2.5"
  :description "Manage local VMs from the REPL"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [slingshot "0.10.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojars.tbatchelli/vboxjws "4.1.8"]
                 [fs "1.0.0"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :multi-deps {"1.3" [[org.clojure/clojure "1.3.0"]]
               ;; "1.4" [[org.clojure/clojure "1.4.0-beta1"]]
               :all [[slingshot "0.10.0"]
                     [org.clojure/tools.logging "0.2.3"]
                     [org.clojars.tbatchelli/vboxjws "4.1.8"]
                     [org.jvnet.hudson/vijava "2120100824"]
                     [fs "1.0.0"]]}
  :dev-dependencies [[robert/hooke "1.1.2"]
                     [log4j/log4j "1.2.14"]
                     [lein-clojars "0.6.0"]]
  :test-selectors {:default (fn [v] (not (:integration v)))
                   :integration :integration
                   :all (fn [_] true)}
  :jar-exclusions [#"log4j.xml"])
