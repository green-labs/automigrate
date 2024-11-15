(ns automigrate.core-errors-test
  (:require [automigrate.core :as core]
            [automigrate.migrations :as migrations]
            [automigrate.testing-config :as config]
            [automigrate.testing-util :as test-util]
            [bond.james :as bond]
            [clojure.test :refer :all]))

(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR-FULL))


(deftest test-run-make-migration-args-error
  (testing "check missing model file path"
    (is (thrown-with-msg? Exception #"Cannot open <nil> as a Reader."
                          (with-out-str
                            (core/make {:migrations-dir config/MIGRATIONS-DIR})))))

  (testing "check missing migrations dir path"
    (is (thrown-with-msg? Exception #"No such file or directory"
                          (with-out-str
                            (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")})))))

  (testing "check wrong type of migration"
    (is (thrown-with-msg? Exception #"Invalid migration type."
                          (with-out-str (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                                                    :migrations-dir config/MIGRATIONS-DIR
                                                    :type "txt"})))))

  (testing "check missing migration name"
    (is (thrown-with-msg? Exception #"Missing migration name."
                          (with-out-str (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                                                    :migrations-dir config/MIGRATIONS-DIR
                                                    :type :empty-sql}))))))


(deftest test-run-migrate-args-error
  (testing "check missing db connection"
    (is (thrown-with-msg? Exception #"-- COMMAND ERROR -------------------------------------\\n\\nMissing database connection URL.\\n\\n  nil\\n"
                          (with-out-str
                            (core/migrate {:migrations-dir config/MIGRATIONS-DIR})))))

  (testing "check invalid target migration number"
    (core/make {:models-file (str config/MODELS-DIR "feed_basic.edn")
                :migrations-dir config/MIGRATIONS-DIR
                :resources-dir config/RESOURCES-DIR})
    (is (thrown-with-msg? Exception #"Invalid target migration number."
                          (with-out-str
                            (core/migrate {:jdbc-url config/DATABASE-URL
                                           :migrations-dir config/MIGRATIONS-DIR
                                           :number 4}))))))


(deftest test-run-explain-args-error
  (testing "check missing db connection"
    (is (thrown-with-msg? Exception #"-- COMMAND ERROR -------------------------------------\\n\\nInvalid direction of migration.\\n\\n  :wrong\\n"
                          (with-out-str
                            (core/explain {:migrations-dir config/MIGRATIONS-DIR
                                           :number 1
                                           :direction :wrong})))))

  (testing "check missing migration by number"
    (is (thrown-with-msg? Exception #"Missing migration by number 10"
                          (with-out-str (core/explain {:migrations-dir config/MIGRATIONS-DIR
                                                       :number 10}))))))


(deftest test-run-unexpected-error
  (testing "check fiction unexpected error"
    #_{:clj-kondo/ignore [:private-call]}
    (bond/with-stub! [[migrations/get-detailed-migrations-to-migrate
                       (fn [& _] (throw (Exception. "Testing error message.")))]]
      (is (thrown-with-msg? Exception #"Testing error message."
                            (with-out-str
                              (core/migrate {:migrations-dir config/MIGRATIONS-DIR
                                             :jdbc-url config/DATABASE-URL})))))))
