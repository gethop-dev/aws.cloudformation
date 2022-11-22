(ns hop-cli.bootstrap.profile.registry.object-storage.s3
  (:require [hop-cli.bootstrap.util :as bp.util]))

(defn- object-storage-adapter-config
  [_settings]
  {:dev.gethop.object-storage/s3 {:bucket-name (tagged-literal 'duct/env ["S3_BUCKET_NAME" 'Str])
                                  :presigned-url-lifespan 30}})

(defn- build-env-variables
  [settings environment]
  {:S3_BUCKET_NAME
   (bp.util/get-settings-value settings [:project :profiles :object-storage-s3 :environment environment :bucket :? :name])})

(defn profile
  [settings]
  {:dependencies '[[dev.gethop/object-storage.s3 "0.6.10"]]
   :environment-variables {:dev (build-env-variables settings :dev)
                           :test (build-env-variables settings :test)
                           :prod (build-env-variables settings :prod)}
   :config-edn {:base (object-storage-adapter-config settings)}})
