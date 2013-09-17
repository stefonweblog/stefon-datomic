(ns stefon-datomic.config
  (:require [clojure.java.io :as io]
            [datomic.api :as datomic]))


(defn get-config-raw []
  (load-string (slurp (io/resource "stefon-datomic.edn"))))

(def get-config (memoize get-config-raw))

;; UTILITY Functions
(defn find-mapping [mkey]

  (let [cfg (get-config)]
    (-> cfg :action-mappings mkey)))
