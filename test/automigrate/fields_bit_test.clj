(ns automigrate.fields-bit-test
  (:require [automigrate.testing-config :as config]
            [automigrate.testing-util :as test-util]
            [clojure.string :as str]
            [clojure.test :refer :all]))


(use-fixtures :each
  (test-util/with-drop-tables config/DATABASE-CONN)
  (test-util/with-delete-dir config/MIGRATIONS-DIR-FULL))


(deftest ^:eftest/slow test-fields-bit-create-table-ok
  (doseq [{:keys [field-type field-name]} [{:field-type :bit}
                                           {:field-type :varbit
                                            :field-name "bit varying"}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions [{:action :create-table
                             :fields {:thing {:type [field-type 3]}}
                             :model-name :account}]
              :q-edn [{:create-table [:account]
                       :with-columns [(list :thing [field-type 3])]}]
              :q-sql [[(format "CREATE TABLE account (thing %s(3))"
                               (str/upper-case (name field-type)))]]}
             (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions []
               :existing-models {:account
                                 {:fields [[:thing [field-type 3]]]}}}))))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length 3
               :column_default nil
               :column_name "thing"
               :data_type (or field-name (name field-type))
               :udt_name (name field-type)
               :is_nullable "YES"
               :table_name "account"}]
             (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-fields-bit-alter-column-ok
  (doseq [{:keys [field-type field-name]} [{:field-type :bit}
                                           {:field-type :varbit
                                            :field-name "bit varying"}]
          :let [type-name-up (str/upper-case (name field-type))]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :alter-column
                                  :changes {:type {:from [field-type 3]
                                                   :to [field-type 10]}}
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type [field-type 10]}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing [field-type 3])
                       :alter-table :account}
                      {:alter-table (list :account
                                          {:alter-column
                                           (list :thing :type [field-type 10]
                                                 :using [:raw "thing"] [:raw "::"] [field-type 10])})}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s(3)"
                               type-name-up)]
                      [(format (str "ALTER TABLE account ALTER COLUMN thing TYPE %s(10)"
                                    " USING thing :: %s(10)")
                               type-name-up type-name-up)]]}
             (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}
                                  {:action :add-column
                                   :field-name :thing
                                   :model-name :account
                                   :options {:type [field-type 3]}}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing [field-type 10]]]}}}))))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}
              {:character_maximum_length 10
               :column_default nil
               :column_name "thing"
               :data_type (or field-name (name field-type))
               :udt_name (name field-type)
               :is_nullable "YES"
               :table_name "account"}]
             (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest ^:eftest/slow test-fields-bit-add-column-ok
  (doseq [{:keys [field-type field-name]} [{:field-type :bit}
                                           {:field-type :varbit
                                            :field-name "bit varying"}]]
    (test-util/drop-all-tables config/DATABASE-CONN)
    (test-util/delete-recursively config/MIGRATIONS-DIR)

    (testing "check generated actions, queries edn and sql from all actions"
      (is (= {:new-actions (list {:action :add-column
                                  :field-name :thing
                                  :model-name :account
                                  :options {:type [field-type 3]}})
              :q-edn [{:create-table [:account]
                       :with-columns ['(:id :serial)]}
                      {:add-column (list :thing [field-type 3])
                       :alter-table :account}]
              :q-sql [["CREATE TABLE account (id SERIAL)"]
                      [(format "ALTER TABLE account ADD COLUMN thing %s(3)"
                               (str/upper-case (name field-type)))]]}
             (test-util/perform-make-and-migrate!
              {:jdbc-url config/DATABASE-CONN
               :existing-actions [{:action :create-table
                                   :fields {:id {:type :serial}}
                                   :model-name :account}]
               :existing-models {:account
                                 {:fields [[:id :serial]
                                           [:thing [field-type 3]]]}}}))))

    (testing "check actual db changes"
      (is (= [{:character_maximum_length nil
               :column_default "nextval('account_id_seq'::regclass)"
               :column_name "id"
               :data_type "integer"
               :udt_name "int4"
               :is_nullable "NO"
               :table_name "account"}
              {:character_maximum_length 3
               :column_default nil
               :column_name "thing"
               :data_type (or field-name (name field-type))
               :udt_name (name field-type)
               :is_nullable "YES"
               :table_name "account"}]
             (test-util/get-table-schema-from-db config/DATABASE-CONN "account"))))))


(deftest test-fields-bit-uses-existing-bit-type
  (let [params {:existing-models
                {:account
                 {:fields [[:thing [:bit]]]}}}]
    (is (thrown-with-msg? Exception #"-- MODEL ERROR -------------------------------------\\n\\nInvalid definition bit type of field :account/thing.\\n\\n  \[:bit\]\\n"
                          (with-out-str
                            (test-util/make-migration! params))))))
