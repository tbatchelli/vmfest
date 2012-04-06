(ns vmfest.virtualbox.system-properties-test
  (:use clojure.test)
  (:use  [clojure.test]
         [vmfest.manager :only [server]]
         [vmfest.virtualbox.session :only [with-vbox]])
  (:use vmfest.virtualbox.system-properties :reload))

(deftest readers
  (testing "getters do not throw exceptions"
    (with-vbox (server "http://localhost:18083") [_ vbox]
      ;; only test that exceptions are not thrown
      (are [x] true
           (min-guest-ram vbox)
           (max-guest-ram vbox)
           (min-guest-vram vbox)
           (max-guest-vram vbox)
           (min-guest-cpu-count vbox)
           (max-guest-cpu-count vbox)
           (max-guest-monitors vbox)
           (info-vd-size vbox)
           (serial-port-count vbox)
           (parallel-port-count vbox)
           (max-boot-position vbox)
           (default-machine-folder vbox)
           (medium-formats vbox)
           (default-hard-disk-format vbox)
           (free-disk-space-warning vbox)
           (free-disk-space-error vbox)
           (free-disk-space-percent-warning vbox)
           (free-disk-space-percent-error vbox)
           (vrde-auth-library vbox)
           (web-service-auth-library vbox)
           (default-vrde-ext-pack vbox)
           (log-history-count vbox)
           (default-audio-driver vbox))))
  (testing "setters"
           ;; setters are not tested because this would screw up your host!
    ))