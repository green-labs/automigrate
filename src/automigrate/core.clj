(ns automigrate.core
  "Public interface for lib's users."
  (:gen-class)
  (:require [automigrate.fields :as fields]
            [automigrate.help :as automigrate-help]
            [automigrate.migrations :as migrations]
            [automigrate.util.file :as file-util]
            [automigrate.util.spec :as spec-util]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [slingshot.slingshot :refer [try+]]
            [slingshot.support :refer [rethrow]])
  (:refer-clojure :exclude [list]))


(def ^:private JDBC-URL-ENV-VAR "DATABASE_URL")

(s/def ::models-file string?)
(s/def ::migrations-dir string?)
(s/def ::resources-dir string?)
(s/def ::jdbc-url (s/and some? (s/conformer str)))
(s/def ::jdbc-url-env-var string?)
(s/def ::number int?)
(s/def ::custom-types (s/coll-of keyword? :kind set?))


(s/def ::cmd
  (s/and
   (s/conformer symbol)
   (set automigrate-help/HELP-CMDS-ORDER)))


(s/def ::format
  (s/and
   (s/conformer keyword)
   #{:sql :human}))


(s/def ::type
  (s/and
   (s/conformer keyword)
   #{migrations/EMPTY-SQL-MIGRATION-TYPE}))


(s/def ::name (s/conformer name))


(s/def ::migrations-table
  (s/and
   string?
   (s/conformer
    (fn [v]
      (keyword (str/replace v #"_" "-"))))))


(s/def ::direction
  (s/and
   (s/conformer keyword)
   #{:forward :backward}))


(s/def ::make-args
  (s/keys
   :opt-un [::type
            ::name
            ::models-file
            ::migrations-dir
            ::resources-dir
            ::custom-types]))


(s/def ::migrate-args
  (s/keys
   :req-un [::jdbc-url]
   :opt-un [::migrations-dir
            ::number
            ::migrations-table
            ::jdbc-url-env-var]))


(s/def ::explain-args
  (s/keys
   :req-un [::number]
   :opt-un [::migrations-dir
            ::direction
            ::format]))


(s/def ::list-args
  (s/keys
   :req-un [::jdbc-url]
   :opt-un [::migrations-table
            ::migrations-dir
            ::jdbc-url-env-var]))


(s/def ::help-args
  (s/keys
   :opt-un [::cmd]))


(defn- run-fn
  [f args args-spec]
  (try+
   (let [args* (spec-util/conform args-spec (or args {}))]
     (f args*))
   (catch [:type ::s/invalid] e
     (file-util/prn-err e) ; type invalid는 모두 여기서 prn-err
     (.flush *out*)
     (rethrow))
   (catch Object e
     (.flush *out*)
     (rethrow))))


; Public interface

(defn make
  "Create a new migration based on changes to the models.

Available options:
  :name - Custom name for a migration. Default: auto-generated name by first action in migration. (optional)
  :type - Type of new migration, empty by default for auto-generated migration.
          Set `:empty-sql` - for creating an empty raw SQL migration. (optional)
  :models-file - Path to the file with model definitions relative to the `resources` dir. Default: `db/models.edn`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :resources-dir - Path to resources dir to create migrations dir, if it doesn't exist. Default: `resources` (optional)
  :custom-types - Set of custom field types to be used in models. Example: #{:dml-type}. (optional)"
  [{:keys [custom-types] :as args}]
  (binding [fields/*custom-types* custom-types]
    (run-fn migrations/make-migration args ::make-args)))


(defn migrate
  "Run existing migrations and change the database schema.

Available options:
  :number - Integer number of the target migration. (optional)
  :jdbc-url - JDBC url for the database connection. Default: get from `DATABASE_URL` env var. (optional)
  :jdbc-url-env-var - Name of environment variable for jdbc-url. Default: `DATABASE_URL`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :migrations-table - Custom name for the migrations table in the database. (optional)
  :custom-types - Set of custom field types to be used in models. Example: #{:dml-type}. (optional)"
  ([]
   ; 0-arity function can be used inside application code if there are no any options.
   (migrate {}))
  ([{:keys [jdbc-url-env-var custom-types] :as args}]
   (binding [fields/*custom-types* custom-types]
     (let [jdbc-url-env-var* (or jdbc-url-env-var JDBC-URL-ENV-VAR)
           args* (update args :jdbc-url #(or % (System/getenv jdbc-url-env-var*)))]
       (run-fn migrations/migrate args* ::migrate-args)))))


(defn explain
  "Show raw SQL or human-readable description for a migration by number.

Available options:
  :number - Integer number of the migration to explain. (required)
  :direction - Direction of the migration to explain, can be `forward` (default) or `backward`. (optional)
  :format - Format of explanation, can be `sql` (default) or `human`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :custom-types - Set of custom field types to be used in models. Example: #{:dml-type}. (optional)"
  [{:keys [custom-types] :as args}]
  (binding [fields/*custom-types* custom-types]
    (run-fn migrations/explain args ::explain-args)))


(defn list
  "Show the list of existing migrations with status.

Available options:
  :jdbc-url - JDBC url for the database connection. Default: get from `DATABASE_URL` env var. (optional)
  :jdbc-url-env-var - Name of environment variable for jdbc-url. Default: `DATABASE_URL`. (optional)
  :migrations-dir - Path to directory containing migration files relative to the `resources` dir. Default: `db/migrations`. (optional)
  :migrations-table - Custom name for the migrations table in the database. Default: `automigrate_migrations`. (optional)"
  [{:keys [jdbc-url-env-var] :as args}]
  (let [jdbc-url-env-var* (or jdbc-url-env-var JDBC-URL-ENV-VAR)
        args* (update args :jdbc-url #(or % (System/getenv jdbc-url-env-var*)))]
    (run-fn migrations/list-migrations args* ::list-args)))


(defn help
  "Help information for all commands of automigrate tool.

Available options:
  :cmd - Command name to display help information for a specific command. (optional)"
  [args]
  (run-fn automigrate-help/show-help! args ::help-args))


; Classic CLI-interface support

(def cli-options-common
  [[nil "--jdbc-url URL"]
   [nil "--jdbc-url-env-var ENV_VAR"]
   [nil "--migrations-dir DIR_PATH"]
   [nil "--models-file FILE_PATH"]
   [nil "--migrations-table TABLE"]
   [nil "--resources-dir DIR"]])


(def cli-options-explain
  (concat
   cli-options-common
   [["-n" "--number NUMBER"
     :parse-fn #(Integer/parseInt %)]
    ["-d" "--direction DIRECTION"]
    ["-f" "--format FORMAT"]]))


(def cli-options-make
  (concat
   cli-options-common
   [[nil "--name NAME"]
    [nil "--type TYPE"]
    [nil "--custom-types TYPES"
     :parse-fn #(set (map keyword (str/split % #",")))]]))


(def cli-options-migrate
  (concat
   cli-options-common
   [["-n" "--number NUMBER"
     :parse-fn #(Integer/parseInt %)]]))


(def cli-options-help
  [[nil "--cmd COMMAND"]])


(defn- parse-opts-or-throw-err
  [args options-spec]
  (let [args-parsed (cli/parse-opts args options-spec)]
    (if (seq (:errors args-parsed))
      (throw (ex-info (format "Command error: %s" (:errors args-parsed)) {}))
      (:options args-parsed))))


(defn -main
  [command & args]
  (case command
    "list" (list (parse-opts-or-throw-err args cli-options-common))
    "migrate" (migrate (parse-opts-or-throw-err args cli-options-migrate))
    "make" (make (parse-opts-or-throw-err args cli-options-make))
    "explain" (explain (parse-opts-or-throw-err args cli-options-explain))
    "help" (help (parse-opts-or-throw-err args cli-options-help))
    (println "ERROR: command does not exist.")))


(comment
  (make {})

  (migrate {:jdbc-url "jdbc:postgresql://localhost:5433/farmmoa?user=developer&password=postgrespasswor"})

  :rcf)
