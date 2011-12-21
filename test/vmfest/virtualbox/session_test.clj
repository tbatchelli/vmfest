(ns vmfest.virtualbox.session-test
  (:use vmfest.virtualbox.session :reload)
  (:use [vmfest.virtualbox.virtualbox :only (find-vb-m)])
  (:use clojure.test
        vmfest.fixtures
        vmfest.virtualbox.model)
  (:import [org.virtualbox_4_1
            VirtualBoxManager]
           [slingshot ExceptionInfo]
           [vmfest.virtualbox.model
            Server
            Machine]))

(deftest ^{:integration true}
  ws-session-test
  (let [mgr (create-session-manager)]
    (try
      (testing "Get ahold of VBox manager"
        (is (= (class mgr)
               VirtualBoxManager)))
      (testing "Connect to the remote vbox server"
        (let [vbox (create-vbox mgr *url* *username* *password*)]
          (testing "Get a connection to the remote VBox Server"
            (is (not (nil? (.getVersion vbox)))))
          (testing "Connecting to a malformed address should throwh a condition"
            (is (thrown-with-msg? ExceptionInfo #"Cannot connect"
                  (create-vbox mgr "bogus address" "" ""))))))
      (finally (when mgr
                 (try (.disconnect mgr) (catch Exception _))))))
  (testing "create-mgr-vbox"
    (let [[mgr vbox] (create-mgr-vbox *url* *username* *password*)]
      (try
        (is (not (nil? (.getVersion vbox))))
        (finally (when mgr
                   (try (.disconnect mgr) (catch Exception _))))))))

(deftest ^{:integration true}
  session-wrappers
  (testing "with-vbox wrapper"
    (is (not (nil? (with-vbox *server* [mgr vbox]
                     (.getVersion vbox)))))))

(def valid-machine (Machine. "Test-1" *server* nil))
(def bogus-machine (Machine. "Bogus name" *server* nil))

(deftest ^{:integration true}
  with-session-tests
  (testing "use write session to update machine definition"
    (is (with-session valid-machine :write [s vb-m]
          (let [next-memory-size (+ 1 (.getMemorySize vb-m))]
            (.setMemorySize vb-m (long next-memory-size))
            (.saveSettings vb-m)
            (= next-memory-size (.getMemorySize vb-m))))))
  (testing "write locks are acquired and released correctly,"
    (testing "write locks are released"
      (is (nil? (do
                  (with-session valid-machine :write [s m])
                  (with-session valid-machine :write [s m])))
          "No locking exceptions are thrown"))
    (testing "write locks are acquired"
      (is (thrown-with-msg? ExceptionInfo #"Cannot open session with machine"
           (with-session valid-machine :write [s m]
             (with-session valid-machine :write [s2 m2])))))
    (testing "shared locks can be acquired after a write lock"
      (is (nil? (do
                  (with-session valid-machine :write [s m]
                    (with-session valid-machine :shared [s2 m2]
                      (with-session valid-machine :shared [s3 m3])))))))
    (testing "a write lock cannot be acquired after a shared lock"
      (is (thrown-with-msg? ExceptionInfo #"Cannot open session with machine"
            (with-session valid-machine :shared [s m]
              (with-session valid-machine :write [s2 m2]))))))
  (testing "a session with a bogus machine will throw a condition"
      (is (thrown-with-msg? ExceptionInfo #"Cannot open session with machine"
            (with-session bogus-machine :write [s m])))))

(deftest ^{:integration true}
  shared-sessions-can-control-machines
  ;(testing "I")
  )

(deftest ^{:integration true}
  with-no-session-tests
  (testing "with no session you can read parameters on the machine"
    (with-no-session valid-machine [vb-m]
      (is (not (nil? vb-m)))
      (is (< 0 (.getMemorySize vb-m))))))

(deftest session-checks
  (testing "Requiring a session matches with the exact session"
    (is (check-session-types :shared :shared))
    (is (check-session-types :write-lock :write-lock)))
  (testing "Requiring a :shared session will be ok with a :write-lock session"
    (is (check-session-types :write-lock :shared))))

(deftest ^{:integration true}
  session-checks-int
  (testing "Session checks work with actual ISession objects"
    (with-session valid-machine :write [session _]
      (is (check-session session :write-lock)))
    (with-session valid-machine :shared [session _]
      (is (check-session session :shared))
      (is (check-session session :write-lock)))))
