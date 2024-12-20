(ns automigrate.indexes
  "Spec for index definitions."
  (:require [automigrate.util.spec :as spec-util]
            [clojure.spec.alpha :as s]))


(s/def ::type
  #{:btree :gin :gist :spgist :brin :hash})


(s/def ::fields
  (s/coll-of keyword? :min-count 1 :kind vector? :distinct true))


(s/def ::unique #{true :nulls-not-distinct})
(s/def ::where (s/and vector? seq))


(s/def ::index
  (s/keys
   :req-un [::type
            ::fields]
   :opt-un [::unique
            ::where]))


(s/def ::index-vec-options
  (s/keys
   :req-un [::fields]
   :opt-un [::unique
            ::where]))


(s/def ::index-vec-options-strict-keys
  (spec-util/validate-strict-keys ::index-vec-options))


(s/def ::index-name keyword?)


(s/def ::index-vec
  (s/cat
   :name ::index-name
   :type ::type
   :options (s/and
             ::index-vec-options
             ::index-vec-options-strict-keys)))


(s/def ::indexes
  (s/map-of keyword? ::index :min-count 1 :distinct true))
