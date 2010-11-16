(ns unit.vmfest.virtualbox.conditions-unit
  (:use [vmfest.virtualbox.conditions] :reload)
  (:use [clojure.test]))

(deftest exception-translation
  (is (= (as-map (java.net.ConnectException.))
         {:type :connection-error})
      "ConnectException is properly translated to a map")
  (is (= (as-map (java.lang.Exception.))
         {:original-message nil
          :cause nil
          :type :exception})
      "Exception is properly translated to a map")
  (comment "TODO: add more tests here"))

