(ns automigrate.fields
  (:require [clojure.spec.alpha :as s]
            [automigrate.util.spec :as spec-util])
  (:import (clojure.lang PersistentVector)))


(def FOREIGN-KEY-OPTION :foreign-key)
(def ON-DELETE-OPTION :on-delete)
(def ON-UPDATE-OPTION :on-update)


(def fk-actions
  #{:cascade
    :set-null
    :set-default
    :restrict
    :no-action})


(s/def ::char-type (s/tuple #{:char :varchar :float} pos-int?))

(s/def ::float-type (s/tuple #{:float} pos-int?))


(s/def ::keyword-type
  ; TODO: switch available fields according to db dialect!
  (s/and
    keyword?
    #{:integer
      :smallint
      :bigint
      :float
      :real
      :serial
      :uuid
      :boolean
      :text
      :timestamp
      :date
      :time
      :point
      :json
      :jsonb}))


(defn- field-type-dispatch
  [v]
  (cond
    (keyword? v) :keyword
    (and (vector? v)
      (contains? #{:char :varchar} (first v))) :char
    (and (vector? v)
      (contains? #{:float} (first v))) :float))


(defmulti field-type field-type-dispatch)


(defmethod field-type :keyword
  [_]
  ::keyword-type)


(defmethod field-type :char
  [_]
  ::char-type)


(defmethod field-type :float
  [_]
  ::float-type)


(s/def ::type (s/multi-spec field-type field-type-dispatch))


(def ^:private type-hierarchy
  (-> (make-hierarchy)
    (derive :smallint :integer)
    (derive :bigint :integer)
    (derive :serial :integer)
    (derive :text :string)
    (derive :varchar :string)
    (derive :char :string)
    (derive :date :timestamp)
    (derive :time :timestamp)
    (derive :uuid :string)
    (derive :bool :boolean)
    (derive :real :float)))


(defn check-type-group
  "Return field type parent or type itself if there is no parent."
  [t]
  (let [field-type-parent (first (parents type-hierarchy t))]
    (or field-type-parent t)))


(s/def ::null boolean?)
(s/def ::primary-key true?)
(s/def ::unique true?)
(s/def ::foreign-key qualified-keyword?)


(s/def ::default-int integer?)
(s/def ::default-float float?)
(s/def ::default-bool boolean?)
(s/def ::default-str string?)
(s/def ::default-nil nil?)


(s/def ::default-fn (s/cat
                      :name keyword?
                      :val (s/? (some-fn integer? float? string?))))


(defmulti default-option class)


(defmethod default-option Long
  [_]
  ::default-int)


(defmethod default-option Double
  [_]
  ::default-float)


(defmethod default-option Boolean
  [_]
  ::default-bool)


(defmethod default-option String
  [_]
  ::default-str)


(defmethod default-option nil
  [_]
  ::default-nil)


(defmethod default-option PersistentVector
  [_]
  (s/and
    ::default-fn
    (s/conformer
      (fn [value]
        (let [fn-name (:name value)
              fn-arg (:val value)]
          (cond-> [fn-name]
            (some? fn-arg) (conj fn-arg)))))))


(s/def ::default
  (s/multi-spec default-option class))


(s/def ::on-delete fk-actions)


(s/def ::on-update fk-actions)


(s/def ::options
  (s/keys
    :opt-un [::null
             ::primary-key
             ::unique
             ::default
             ::foreign-key
             ::on-delete
             ::on-update]))


(s/def ::options-strict-keys
  (spec-util/validate-strict-keys ::options))


(s/def ::validate-fk-options-on-delete
  ; Validate that basic fields mustn't contain foreign-key specific options.
  (fn [value]
    (let [option-names (-> (keys value) set)]
      (if (not (contains? option-names FOREIGN-KEY-OPTION))
        (not (contains? option-names ON-DELETE-OPTION))
        true))))


(s/def ::validate-fk-options-on-update
  ; Validate that basic fields mustn't contain foreign-key specific options.
  (fn [value]
    (let [option-names (-> (keys value) set)]
      (if (not (contains? option-names FOREIGN-KEY-OPTION))
        (not (contains? option-names ON-UPDATE-OPTION))
        true))))


(s/def ::options-strict
  (s/and
    ::options
    ::options-strict-keys
    ::validate-fk-options-on-delete
    ::validate-fk-options-on-update))


(s/def ::validate-default-and-null
  (fn [{:keys [null default] :as options}]
    (not (and (false? null)
           (nil? default)
           (contains? options :default)))))


(s/def ::validate-fk-options-and-null-on-delete
  (fn [{:keys [null on-delete]}]
    (not (and (false? null) (= :set-null on-delete)))))


(s/def ::validate-fk-options-and-null-on-update
  (fn [{:keys [null on-update]}]
    (not (and (false? null) (= :set-null on-update)))))


(defmulti validate-default-and-type
  (fn [{field-type-val :type}]
    (if (vector? field-type-val)
      (first field-type-val)
      field-type-val))
  :hierarchy #'type-hierarchy)


(defmethod validate-default-and-type :integer
  [{:keys [default]}]
  (or (integer? default) (nil? default)))


(defmethod validate-default-and-type :string
  [{:keys [default]}]
  (or (string? default) (nil? default)))


(defmethod validate-default-and-type :boolean
  [{:keys [default]}]
  (or (boolean? default) (nil? default)))


(defmethod validate-default-and-type :timestamp
  [{:keys [default]}]
  (or (s/valid? (s/tuple #{:now}) default) (nil? default)))


(defmethod validate-default-and-type :float
  [{:keys [default]}]
  (or (float? default) (nil? default)))


(defmethod validate-default-and-type :default
  [_]
  true)


(s/def ::validate-default-and-type
  validate-default-and-type)


(s/def ::field-with-type
  (s/merge
    (s/keys
      :req-un [::type])
    ::options))


(s/def ::field
  (s/and
    ; TODO: add ::field-with-type-strict-keys
    ::field-with-type
    ::validate-default-and-null
    ::validate-fk-options-and-null-on-delete
    ::validate-fk-options-and-null-on-update
    ::validate-default-and-type))


(s/def ::field-name keyword?)


(s/def ::field-vec
  (s/cat
    :name ::field-name
    :type ::type
    :options (s/? ::options-strict)))


(s/def ::fields
  (s/map-of ::field-name ::field :min-count 1 :distinct true))