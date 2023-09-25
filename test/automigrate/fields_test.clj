(ns automigrate.fields-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [automigrate.fields :as fields]))


(deftest test-validate-fk-options-on-delete
  (testing "check no fk and no on-delete ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-delete {:null true}))))
  (testing "check fk and on-delete ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-delete {:null true
                                                            :foreign-key :account/id
                                                            :on-delete :cascade}))))
  (testing "check no fk and on-delete err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-on-delete {:null true
                                                            :on-delete :cascade})))))


(deftest test-validate-fk-options-on-update
  (testing "check no fk and no on-update ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-update {:null true}))))
  (testing "check fk and on-update ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-on-update {:null true
                                                            :foreign-key :account/id
                                                            :on-update :cascade}))))
  (testing "check no fk and on-delete err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-on-update {:null true
                                                            :on-update :cascade})))))


(deftest test-validate-default-with-null
  (testing "check default is nil and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-null {:null true
                                                        :default nil}))))
  (testing "check default is nil and no null ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-null {:default nil}))))
  (testing "check no default and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-null {:null false}))))
  (testing "check default is nil and null is false err"
    (is (false?
          (s/valid? ::fields/validate-default-and-null {:null false
                                                        :default nil})))))


(deftest test-validate-default-with-type
  (testing "check default is int and type integer ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :integer
                                                        :default 10}))))
  (testing "check default is string and type integer err"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type :integer
                                                        :default "wrong"}))))
  (testing "check default is int and type varchar err"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type [:varchar 20]
                                                        :default 10}))))
  (testing "check default is int and type timestamp ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :timestamp
                                                        :default [:now]}))))

  (testing "check default is int and type float ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :float
                                                        :default 10.0}))))
  (testing "check default is int and type float err"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type :float
                                                        :default 10}))))
  (testing "check default is int and type float as nil ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :float
                                                        :default nil}))))

  (testing "check default is numeric str and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default "10.32"}))))
  (testing "check default is non numeric str and type decimal ok"
    (is (false?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default "wrong"}))))
  (testing "check default is bigdec and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default 10.32M}))))
  (testing "check default is int and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default 10}))))
  (testing "check default is float and type decimal ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default 10.3}))))
  (testing "check default is int and type decimal as nil ok"
    (is (true?
          (s/valid? ::fields/validate-default-and-type {:type :decimal
                                                        :default nil})))))


(deftest test-validate-fk-options-and-null
  (testing "check on-delete is cascade and null is true ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-and-null-on-delete {:null true
                                                                     :on-delete :cascade}))))
  (testing "check on-delete is cascade and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-and-null-on-delete {:null false
                                                                     :on-delete :cascade}))))
  (testing "check on-delete not exists and null is false ok"
    (is (true?
          (s/valid? ::fields/validate-fk-options-and-null-on-update {:null false}))))
  (testing "check on-delete is set-null and null is false err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-and-null-on-delete {:null false
                                                                     :on-delete :set-null}))))
  (testing "check on-upate is set-null and null is false err"
    (is (false?
          (s/valid? ::fields/validate-fk-options-and-null-on-update {:null false
                                                                     :on-update :set-null})))))
