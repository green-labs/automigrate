(ns automigrate.models
  "Module for for transforming models to migrations."
  (:require [clojure.spec.alpha :as s]
            [slingshot.slingshot :refer [throw+]]
            [clojure.set :as set]
            [automigrate.util.model :as model-util]
            [automigrate.util.spec :as spec-util]
            [automigrate.util.validation :as validation-util]
            [automigrate.fields :as fields]
            [automigrate.indexes :as indexes]
            [automigrate.types :as types])
  (:import (clojure.lang PersistentVector PersistentArrayMap)))


(s/def ::model
  (s/keys
   :req-un [::fields/fields]
   :opt-un [::indexes/indexes
            ::types/types]))


(defn- item-vec->map
  [acc field]
  (let [options (assoc (:options field) :type (:type field))]
    (assoc acc (:name field) options)))


(s/def ::item-vec->map
  (s/conformer #(reduce item-vec->map {} %)))


(s/def ::map-kw->kebab-case
  (s/conformer model-util/map-kw-keys->kebab-case))


(s/def ::validate-fields-duplication
  (fn [fields]
    (model-util/has-duplicates? (map :name fields))))


(s/def :automigrate.models.fields->internal/fields
  (s/and
   ::validate-fields-duplication
   ::item-vec->map
   ::map-kw->kebab-case))


(s/def ::validate-indexes-duplication
  (fn [indexes]
    (model-util/has-duplicates? (map :name indexes))))


(s/def :automigrate.models.indexes->internal/indexes
  (s/and
   ::validate-indexes-duplication
   ::item-vec->map
   ::map-kw->kebab-case))


(s/def ::validate-types-duplication
  (fn [types]
    (model-util/has-duplicates? (map :name types))))


(s/def :automigrate.models.types->internal/types
  (s/and
   ::validate-types-duplication
   ::item-vec->map
   ::map-kw->kebab-case))


(s/def ::model->internal
  (s/keys
   :req-un [:automigrate.models.fields->internal/fields]
   :opt-un [:automigrate.models.indexes->internal/indexes
            :automigrate.models.types->internal/types]))


(defn- check-referenced-model-exists?
  "Check that referenced model exists."
  [models qualified-field-name fk-model-name]
  (when-not (contains? models fk-model-name)
    (throw+ {:type ::missing-referenced-model
             :title "MODEL ERROR"
             :data {:referenced-model fk-model-name
                    :fk-field qualified-field-name}
             :message (format "Foreign key %s has reference on the missing model %s."
                              qualified-field-name
                              fk-model-name)})))


(defn- check-referenced-field-exists?
  "Check that referenced field exists in referenced model."
  [fk-field-options qualified-field-name fk-model-name fk-field-name]
  (when-not (some? fk-field-options)
    (let [qualified-fk-field-name (keyword (name fk-model-name) (name fk-field-name))]
      (throw+ {:type ::missing-referenced-field
               :title "MODEL ERROR"
               :data {:referenced-model fk-model-name
                      :referenced-field fk-field-name}
               :message (format "Foreign key %s has reference on the missing field %s."
                                qualified-field-name
                                qualified-fk-field-name)}))))


(defn- check-fields-type-valid?
  "Check that referenced and origin fields has same types.

  Also check that field should have `:unique` option enabled, and
  it has the same type as origin field."
  [qualified-field-name field-options fk-field-options fk-model-name fk-field-name]
  (when-not (or (true? (:unique fk-field-options))
                (true? (:primary-key fk-field-options)))
    (let [qualified-fk-field-name (keyword (name fk-model-name) (name fk-field-name))]
      (throw+ {:type ::referenced-field-is-not-unique
               :title "MODEL ERROR"
               :data {:referenced-model fk-model-name
                      :referenced-field fk-field-name}
               :message (format "Foreign key %s there is no unique or primary key constraint on the referenced field %s."
                                qualified-field-name
                                qualified-fk-field-name)})))

  (let [field-type-group (fields/check-type-group (:type field-options))
        fk-field-type-group (fields/check-type-group (:type fk-field-options))
        qualified-fk-field-name (keyword (name fk-model-name) (name fk-field-name))]
    (when-not (and (some? field-type-group)
                   (some? fk-field-type-group)
                   (= field-type-group fk-field-type-group))
      (throw+ {:type ::fk-fields-have-different-types
               :title "MODEL ERROR"
               :data {:origin-field qualified-field-name
                      :referenced-field qualified-fk-field-name}
               :message (format "Foreign key field %s and referenced field %s have different types."
                                qualified-field-name
                                qualified-fk-field-name)}))))


(defn- validate-foreign-key
  [models]
  (doseq [[model-name model-value] models]
    (doseq [[field-name field-options] (:fields model-value)
            :let [qualified-field-name (keyword (name model-name) (name field-name))
                  [fk-model-name fk-field-name] (model-util/kw->vec
                                                 (:foreign-key field-options))
                  fk-field-options (get-in models [fk-model-name :fields fk-field-name])]]
      (when (and (some? fk-model-name) (some? fk-field-name))
        (check-referenced-model-exists? models qualified-field-name fk-model-name)
        (check-referenced-field-exists? fk-field-options qualified-field-name fk-model-name fk-field-name)
        (check-fields-type-valid?
         qualified-field-name
         field-options
         fk-field-options
         fk-model-name
         fk-field-name))))
  true)


(s/def ::validate-indexes-duplication-across-models
  (fn [models]
    (->> (vals models)
         (mapcat (comp keys :indexes))
         (model-util/has-duplicates?))))


(s/def ::validate-indexed-fields
  (fn [model]
    (let [index-fields (->> (:indexes model)
                            (map #(get-in % [:options :fields]))
                            (flatten)
                            (set))
          model-fields (set (map :name (:fields model)))
          missing-fields (set/difference index-fields model-fields)]
      (empty? missing-fields))))


(s/def ::validate-types-duplication-across-models
  (fn [models]
    (->> (validation-util/get-all-types models)
         (model-util/has-duplicates?))))


(s/def ::validate-enum-field-misses-type
  (fn [models]
    (let [all-types (set (validation-util/get-all-types models))
          all-fields-no-type (validation-util/get-all-enum-fields-without-type
                              models all-types)]
      (not (some? (seq all-fields-no-type))))))


(s/def ::internal-models
  (s/and
   (s/map-of keyword? ::model)
   validate-foreign-key
   ::validate-indexes-duplication-across-models
   ::validate-types-duplication-across-models
   ::validate-enum-field-misses-type))


(s/def :automigrate.models.fields-vec/fields
  (s/coll-of ::fields/field-vec :min-count 1 :kind vector? :distinct true))


(s/def :automigrate.models.indexes-vec/indexes
  (s/coll-of ::indexes/index-vec :min-count 1 :kind vector? :distinct true))


(s/def :automigrate.models.types-vec/types
  (s/coll-of ::types/type-vec :min-count 1 :kind vector? :distinct true))


(s/def ::public-model-as-vec
  :automigrate.models.fields-vec/fields)


(s/def ::public-model-as-map
  (s/keys
   :req-un [:automigrate.models.fields-vec/fields]
   :opt-un [:automigrate.models.indexes-vec/indexes
            :automigrate.models.types-vec/types]))


(s/def ::public-model-as-map-strict-keys
  (spec-util/validate-strict-keys ::public-model-as-map))


(defmulti public-model class)


(defmethod public-model PersistentVector
  [_]
  ::public-model-as-vec)


(defmethod public-model PersistentArrayMap
  [_]
  (s/and
   ::public-model-as-map
   ::public-model-as-map-strict-keys
   ::validate-indexed-fields))


(s/def ::public-model
  (s/multi-spec public-model class))


(s/def ::simplified-model->named-parts
  (s/conformer
   (fn [models]
     (reduce-kv
      (fn [m k v]
        (if (vector? v)
          (assoc m k {:fields v})
          (assoc m k v)))
      {}
      models))))


(s/def ::->internal-models
  (s/and
   (s/map-of keyword? ::public-model)
   ::simplified-model->named-parts
   (s/map-of keyword? ::model->internal)
   ::internal-models))


(defn ->internal-models
  "Transform public models from file to internal representation."
  [models]
  (spec-util/conform ::->internal-models models))
