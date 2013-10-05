(ns stefon-datomic.crud-tags-spec

  (:require [speclj.core :refer :all]
            [datomic.api :as datomic]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]

            [stefon.shell :as shell]
            [stefon.shell.plugin :as plugin]
            [stefon-datomic.plugin :as pluginD]
            [stefon-datomic.crud :as crud]))


(def config (load-string (slurp (io/resource "stefon-datomic.edn"))))
(def domain-schema {:assets
                    [{:name :id, :cardinality :one, :type :string}
                     {:name :name, :cardinality :one, :type :string}
                     {:name :type, :cardinality :one, :type :string}
                     {:name :asset, :cardinality :one, :type :string}],
                    :posts
                    [{:name :id, :cardinality :one, :type :string}
                     {:name :title, :cardinality :one, :type :string}
                     {:name :content, :cardinality :one, :type :string}
                     {:name :content-type, :cardinality :one, :type :string}
                     {:name :created-date, :cardinality :one, :type :instant}
                     {:name :modified-date, :cardinality :one, :type :instant}],
                    :tags
                    [{:name :id, :cardinality :one, :type :string}
                     {:name :name, :cardinality :one, :type :string}]})


(defn populate-with-tags []

  ;; create DB & get the connection
  (let [
        result (pluginD/bootstrap-stefon)
        conn (:conn result)

        ;; add datom

        one (crud/create conn :tag {:name "datomic"})
        two (crud/create conn :tag {:name "clojure"})
        three (crud/create conn :tag {:name "programming"})]

    conn))

(describe "Plugin should be able to capture and persist CRUD messages from a Stefon instance => "

          (before (datomic/delete-database (-> config :dev :url))
                  (shell/stop-system))



          ;; ====
          ;; make CRUD functions from generated schema


          ;;  tag(s)
          (it "Should save created tag(s) to Datomic"

              (let [;; create DB & get the connection
                    result (pluginD/bootstrap-stefon)

                    ;; add datom

                    one (crud/create (:conn result) :tag {:name "datomic"})

                    qresult (datomic/q '[:find ?e :where [?e :tags/name]] (datomic/db (:conn result)))]

                (should= java.util.HashSet (type qresult))
                (should-not (empty? qresult))))

          (it "Should retrieve a created entity tag from Datomic - 001"

              ;; create 3, then get anyone of them - the second
              (let [;; create DB & get the connection
                    result (pluginD/bootstrap-stefon)

                    ;; add datom
                    one (crud/create (:conn result) :tag {:name "datomic"})
                    qresult (crud/retrieve-entity (:conn result) :tag {:name "datomic"}) ]

                (should= java.util.HashSet (type qresult))
                (should-not (empty? qresult))))

          (it "Should retrieve a created tag from Datomic - 002"

              ;; create 3, then get anyone of them - the second
              (let [conn (populate-with-tags)

                    qresult (crud/retrieve conn :tag {:name "clojure"})

                    eid (:db/id (first qresult))
                    uresult (crud/retrieve-by-id conn eid)]

                (println "retrieve-by-id RESULT > " uresult)

                (should (map? uresult))
                (should= '(:db/id :tags/name :tags/id) (keys uresult))))

          (it "Should update a created tag from Datomic"

              ;; create 3, then update anyone of them - the third
              (let [
                    conn (populate-with-tags)

                    qresult (crud/retrieve conn :tag {:name "datomic"}) ]

                (should (seq? qresult))
                (should-not (empty? qresult))
                (should= 1 (count qresult))

                ;; now the UPDATE
                (let [
                      eid (:db/id (first qresult))

                      udt-before (assoc (into {} (first qresult))
                                   :db/id eid  ;; for some reason :db/id gets lost... putting it back
                                   :tags/name "fubar" )
                      udt-after (crud/update conn :tag udt-before)

                      result-after (crud/retrieve conn :tag {:tags/name "fubar"}) ]

                  (should-not (empty? result-after))
                  (should= "fubar" (-> result-after first :tags/name)))))

          (it "Should delete a created tag from Datomic"

              ;; create 3, then delete anyone of them - the first
              (let [
                    conn (populate-with-tags)

                    qresult (crud/retrieve conn :tag {:name "clojure"}) ]

                (should (seq? qresult))
                (should-not (empty? qresult))
                (should= 1 (count qresult))


                ;; now the DELETE
                (let [
                      eid (:db/id (first qresult))

                      one (crud/delete conn eid)
                      result-after (crud/retrieve conn :tag {:tags/name "fubar"}) ]

                  (should (empty? result-after)) )))


          (it "Should list created tags"

              ;; create 3, then list them out... from the DB
              (let [conn (populate-with-tags)
                    qresult (crud/list conn :tags)]

                (println "Listing created tags > " qresult)
                (should-not (empty? qresult))
                (should= 3 (count qresult))))

          ;;  asset(s) - binary data is in Fressian (https://github.com/Datomic/fressian)
          ;;  tag(s)
          ;;  find-by relationships
          ;;    posts > tags
          ;;    tags > posts
          ;;    assets > post

          )
