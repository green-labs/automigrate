(ns tuna.fields-test
  (:require [clojure.test :refer :all]
            [tuna.fields :as fields]))


(deftest test-validate-fk-options
  (testing "check no fk and no on-delete ok"
    (is (true?
          (#'fields/validate-fk-options {:null true}))))
  (testing "check fk and on-delete ok"
    (is (true?
          (#'fields/validate-fk-options {:null true
                                         :foreign-key :account/id
                                         :on-delete :cascade
                                         :on-update :cascade}))))
  (testing "check no fk and on-delete err"
    (is (false?
          (#'fields/validate-fk-options {:null true
                                         :on-delete :cascade
                                         :on-update :cascade})))))


(deftest test-validate-default-with-null
  (testing "check default is nil and null is false ok"
    (is (true?
          (#'fields/validate-default-and-null {:null true
                                               :default nil}))))
  (testing "check default is nil and no null ok"
    (is (true?
          (#'fields/validate-default-and-null {:default nil}))))
  (testing "check no default and null is false ok"
    (is (true?
          (#'fields/validate-default-and-null {:null false}))))
  (testing "check default is nil and null is false err"
    (is (false?
          (#'fields/validate-default-and-null {:null false
                                               :default nil})))))


(deftest test-validate-default-with-type
  (testing "check default is int and type integer ok"
    (is (true?
          (#'fields/validate-default-and-type {:type :integer
                                               :default 10}))))
  (testing "check default is string and type integer err"
    (is (false?
          (#'fields/validate-default-and-type {:type :integer
                                               :default "wrong"}))))
  (testing "check default is int and type varchar err"
    (is (false?
          (#'fields/validate-default-and-type {:type [:varchar 20]
                                               :default 10}))))
  (testing "check default is int and type timestamp ok"
    (is (true?
          (#'fields/validate-default-and-type {:type :timestamp
                                               :default [:now]}))))
  (testing "check default is int and type float ok"
    (is (true?
          (#'fields/validate-default-and-type {:type :float
                                               :default 10.0}))))
  (testing "check default is int and type float err"
    (is (false?
          (#'fields/validate-default-and-type {:type :float
                                               :default 10}))))
  (testing "check default is int and type float as nil ok"
    (is (true?
          (#'fields/validate-default-and-type {:type :float
                                               :default nil})))))