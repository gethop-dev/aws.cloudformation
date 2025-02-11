;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns hop-cli.bootstrap.profile.registry.persistence.sql
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [hop-cli.bootstrap.profile.registry :as registry]
            [hop-cli.bootstrap.util :as bp.util]
            [meta-merge.core :refer [meta-merge]]))

(defn- common-config
  [settings]
  (let [project-name (bp.util/get-settings-value settings :project/name)]
    {:p-adapter (tagged-literal 'ig/ref (keyword (format "%s.boundary.adapter.persistence.sql/postgres" project-name)))}))

(defn- sql-config
  [settings]
  (let [project-name (bp.util/get-settings-value settings :project/name)]
    {[(keyword (format "%s.boundary.adapter.persistence/sql" project-name))
      (keyword (format "%s.boundary.adapter.persistence.sql/postgres" project-name))]
     (tagged-literal 'ig/ref :duct.database/sql)}))

(defn- hikaricp-config
  [_]
  {:duct.database.sql/hikaricp
   {:adapter (tagged-literal 'duct/env ["APP_DB_TYPE" 'Str])
    :server-name (tagged-literal 'duct/env ["APP_DB_HOST" 'Str])
    :port-number (tagged-literal 'duct/env ["APP_DB_PORT" 'Str])
    :database-name (tagged-literal 'duct/env ["APP_DB_NAME" 'Str])
    :username (tagged-literal 'duct/env ["APP_DB_USER" 'Str])
    :password (tagged-literal 'duct/env ["APP_DB_PASSWORD" 'Str])
    :re-write-batched-inserts true
    :logger nil
    :minimum-idle 10
    :maximum-pool-size 25}})

(defn- build-ragtime-config-key
  [settings environment]
  (let [project-name (bp.util/get-settings-value settings :project/name)]
    [:duct.migrator/ragtime
     (keyword (format "%s/%s" project-name (name environment)))]))

(defn- ragtime-config
  [settings]
  {(build-ragtime-config-key settings :prod)
   {:database (tagged-literal 'ig/ref :duct.database/sql)
    :logger (tagged-literal 'ig/ref :duct/logger)
    :strategy :raise-error
    :migrations-table "ragtime_migrations"
    :migrations []}})

(defn- dev-ragtime-config
  [settings]
  {(build-ragtime-config-key settings :dev)
   {:database (tagged-literal 'ig/ref :duct.database/sql)
    :logger (tagged-literal 'ig/ref :duct/logger)
    :strategy :raise-error
    :migrations-table "ragtime_migrations_dev"
    :fake-dependency-to-force-initialization-order
    (tagged-literal 'ig/ref (build-ragtime-config-key settings :prod))
    :migrations []}})

(defn- build-env-variables
  [settings environment]
  (let [base-path [:project :profiles :persistence-sql :deployment (bp.util/get-env-type environment) :?]
        env-path (conj base-path :environment environment)
        deploy-type (bp.util/get-settings-value settings (conj base-path :deployment-type))
        host (if (= :container deploy-type)
               "postgres"
               (bp.util/get-settings-value settings (conj env-path :database :host)))
        type (if (= :container deploy-type)
               "postgresql"
               (bp.util/get-settings-value settings (conj env-path :database :type)))
        db (bp.util/get-settings-value settings (conj env-path :database :name))
        app-user (bp.util/get-settings-value settings (conj env-path :database :app-user :username))
        app-password (bp.util/get-settings-value settings (conj env-path :database :app-user :password))
        app-schema (bp.util/get-settings-value settings (conj env-path :database :app-user :schema))
        admin-user (bp.util/get-settings-value settings (conj env-path :database :admin-user :username))
        admin-password (bp.util/get-settings-value settings (conj env-path :database :admin-user :password))
        container-memory-limit (when (= :container deploy-type)
                                 (bp.util/get-settings-value settings (conj env-path :database :memory-limit-mb)))
        persistent-data-dir (bp.util/get-settings-value settings (conj base-path :persistent-data-dir :?))]
    (merge {:APP_DB_TYPE type
            :APP_DB_HOST host
            :APP_DB_PORT "5432"
            :APP_DB_NAME db
            :APP_DB_USER app-user
            :APP_DB_PASSWORD app-password
            :APP_DB_SCHEMA app-schema
            :DB_HOST host
            :DB_PORT "5432"
            :DB_NAME db
            :DB_ADMIN_USER admin-user
            :DB_ADMIN_PASSWORD admin-password}
           (when container-memory-limit
             {:MEMORY_LIMIT_POSTGRES (str container-memory-limit "m")})
           (when persistent-data-dir
             {:PERSISTENT_DATA_DIR persistent-data-dir}))))

(defn- build-docker-compose-files
  [settings]
  (let [common ["docker-compose.postgres.yml"]
        common-dev-ci ["docker-compose.postgres.common-dev-ci.yml"]
        ci ["docker-compose.postgres.ci.yml"]
        to-deploy ["docker-compose.postgres.to-deploy.yml"]
        dev-deployment-type (bp.util/get-settings-value settings :project.profiles.persistence-sql.deployment.to-develop.?/deployment-type)
        deploy-deployment-type (bp.util/get-settings-value settings :project.profiles.persistence-sql.deployment.to-deploy.?/deployment-type)]
    (cond->  {:to-develop [] :ci [] :to-deploy []}
      (= :container dev-deployment-type)
      (assoc :to-develop (concat common common-dev-ci)
             :ci (concat common common-dev-ci ci))

      (= :container deploy-deployment-type)
      (assoc :to-deploy (concat common to-deploy)))))

(defn- build-docker-files-to-copy
  [settings]
  (bp.util/build-profile-docker-files-to-copy
   (build-docker-compose-files settings)
   "persistence/sql/"
   []))

(defn- build-profile-env-outputs
  [settings env]
  (let [dev-deployment-type (bp.util/get-settings-value settings :project.profiles.persistence-sql.deployment.to-develop.?/deployment-type)
        deploy-deployment-type (bp.util/get-settings-value settings :project.profiles.persistence-sql.deployment.to-deploy.?/deployment-type)
        deploy-deployment-choice (bp.util/get-settings-value settings :project.profiles.persistence-sql.deployment.to-deploy/value)]
    (cond-> {}
      (and
       (= :dev env)
       (= :container dev-deployment-type))
      (assoc-in [:deployment :to-develop :container :environment :dev :database] {:host "postgres"
                                                                                  :port "5432"})

      (= :container deploy-deployment-type)
      (assoc-in [:deployment :to-deploy deploy-deployment-choice :environment env :database] {:host "postgres"
                                                                                              :port "5432"}))))

(defn- replace-env-variable
  [settings environment [env-var-str env-var-name]]
  (let [path [:project :environment-variables environment (keyword env-var-name)]
        env-var-value (bp.util/get-settings-value settings path)]
    (if env-var-value
      env-var-value
      env-var-str)))

(defn build-environment-init-db-sql-string
  [settings environment]
  (let [project-dir (bp.util/get-settings-value settings :project/target-dir)
        template-file (format "%s/postgres/init-scripts/%s/%s/01_create_schemas_and_roles.sql"
                              project-dir
                              (name (bp.util/get-env-type environment))
                              (name environment))
        template-content (slurp (fs/file template-file))]
    (str/replace template-content #"\$\{([^}]+)\}" #(replace-env-variable settings environment %1))))

(defn build-post-installation-messages
  [settings]
  {:test
   [(with-out-str
      (println "Once the DB is up and running you need to run the following SQL statements:\n")
      (println (build-environment-init-db-sql-string settings :test)))]
   :prod
   [(with-out-str
      (println "Once the DB is up and running you need to run the following SQL statements:\n")
      (println (build-environment-init-db-sql-string settings :prod))
      (println "The scripts are stored in the project under postgres/init-scripts for reference."))]})

(defmethod registry/pre-render-hook :persistence-sql
  [_ settings]
  (let [deployment-choice-name (-> (bp.util/get-settings-value
                                    settings :project.profiles.persistence-sql.deployment.to-deploy/value)
                                   (name))]
    {:dependencies '[[duct/migrator.ragtime "0.3.2"]
                     [dev.gethop/database.sql.hikaricp "0.4.1"]
                     [com.github.seancorfield/next.jdbc "1.3.955"]
                     [camel-snake-kebab/camel-snake-kebab "0.4.3"]
                     [dev.weavejester/medley "1.8.1"]
                     [org.postgresql/postgresql "42.7.4"]]
     :config-edn {:base (merge (sql-config settings)
                               (hikaricp-config settings)
                               (ragtime-config settings))
                  :dev (dev-ragtime-config settings)
                  :common-config (common-config settings)}
     :environment-variables {:dev (build-env-variables settings :dev)
                             :test (build-env-variables settings :test)
                             :prod (build-env-variables settings :prod)}
     :files (concat [{:src "persistence/sql/app"
                      :dst "app"}
                     {:src (str "persistence/sql/postgres/init-scripts/to-deploy/prod/"
                                deployment-choice-name)
                      :dst "postgres/init-scripts/to-deploy/prod"}
                     {:src (str "persistence/sql/postgres/init-scripts/to-deploy/test/"
                                deployment-choice-name)
                      :dst "postgres/init-scripts/to-deploy/test"}
                     {:src "persistence/sql/postgres/init-scripts/to-develop/dev"
                      :dst "postgres/init-scripts/to-develop/dev"}]
                    (build-docker-files-to-copy settings))
     :docker-compose (build-docker-compose-files settings)
     :extra-app-docker-compose-environment-variables ["APP_DB_TYPE"
                                                      "APP_DB_HOST"
                                                      "APP_DB_PORT"
                                                      "APP_DB_NAME"
                                                      "APP_DB_USER"
                                                      "APP_DB_PASSWORD"]
     :outputs (meta-merge
               (build-profile-env-outputs settings :dev)
               (build-profile-env-outputs settings :test)
               (build-profile-env-outputs settings :prod))}))

(defmethod registry/post-render-hook :persistence-sql
  [_ settings]
  {:post-installation-messages (build-post-installation-messages settings)})
