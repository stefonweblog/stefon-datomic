(ns stefon-datomic.crud
  (:require [clojure.string :as string]
            [datomic.api :as d]
            [stefon-datomic.config :as config]))


(defn find-mapping [mkey]

  (let [cfg (config/get-config)]
    (-> cfg :action-mappings mkey)))

(defn add-entity-ns
  "Turns a datom-map like A into B, given an entity-key of :post

   A) {:title t :content c :content-type c/t :created-date 0000 :modified-date 1111}
   B) {:post/title t :post/content c :post/content-type c/t :post/created-date 0000 :post/modified-date 1111}"
  [ekey datom-map]
  (reduce-kv (fn [a k v]
               (assoc a (keyword
                         (name ekey)
                         (name k))
                      v))
             {}
             datom-map))

(defn hset-to-cset
  "Put java.util.HashSet into a regular Clojure set"
  [hset]
  (map first (into #{} hset)))

(defn vivify-datomic-entity [the-db eid]
  (d/touch (d/entity the-db eid)))


(defn create [conn domain-key datom-map]

  {:pre [(keyword? domain-key)
         (map? datom-map)]}

  (let [
        one (str "plugin." (name domain-key) ".create")
        lookup-key (keyword one)

        ;; find the mapping
        mapping (find-mapping lookup-key)

        ;; insert mapped function & preamble
        mapped-fn (first mapping)
        mapped-preamble (second mapping)  ;; TODO - can't execute this

        ;; add namespace to map keys
        entity-w-ns (add-entity-ns :posts datom-map)

        adatom (assoc entity-w-ns :db/id (datomic.api/tempid :db.part/user)) ]

    ;; transact to Datomic
    @(datomic.api/transact conn [adatom])))


(defn retrieve-entity [conn constraint-map]

  (let [constraints-w-ns (add-entity-ns :posts constraint-map)


        ;; We expect a structure like... ((:posts/title t) (:posts/content-type c/t))... at the end, we need to double-quote the name
        names-fn #(-> % first name (string/split #"/") first (->> (str "?")) symbol #_(->> (list (symbol 'quote))))
        param-names (map names-fn
                           (seq constraints-w-ns))
        param-values (into [] (map last (seq constraints-w-ns)))


        ;; Should provide constraints that look like: [[?e :posts/title ?title] [?e :posts/content-type ?content-type]]
        constraints-final (->> constraints-w-ns
                               seq
                               (map (fn [inp]
                                      ['?e (first inp) (names-fn inp)] ))
                               (into []))

        ;;
        expression-final {:find ['?e]
                          :in ['$ (into [] param-names)]
                          :where constraints-final}

        ;;
        the-db (datomic.api/db conn)]

    (println "FINAL clause > main > " expression-final)
    (datomic.api/q expression-final the-db param-values) ))

(defn retrieve-entity-1
  ;; lg: I have pasted this directly from my answer from
  ;; stackoverflow. tested it, works. whats wrong with it?
  [conn constraint-map]
  (let [name-fn (comp symbol
                      (partial str "?")
                      name)
        param-names (map name-fn
                         (keys constraint-map))
        param-vals (vals constraint-map)
        constraint-map (add-entity-ns :posts constraint-map)
        where-clause (map #(vector '?e % %2)
                          (keys constraint-map)
                          param-names)
        in-clause (conj param-names '$)
        final-clause (concat [:find '?e]
                             [:in] in-clause
                             [:where] where-clause)]

    (println "FINAL clause > one > " final-clause)
    (apply d/q final-clause (d/db conn) param-vals)))

(defn retrieve-entity-2
  ;; lg: i don't know what you meant by being able to pass an array of
  ;; args (since the args are already present in the constraint-map i
  ;; don't see why that would make sense. Here is a version that
  ;; returns an incomplete query function that you can invoke with
  ;; such a map (only last line modified)
  [conn constraint-map]
  (let [name-fn (comp symbol
                      (partial str "?")
                      name)
        param-names (map name-fn
                         (keys constraint-map))
        param-vals (vals constraint-map)
        constraint-map (add-entity-ns :posts constraint-map)
        where-clause (map #(vector '?e % %2)
                          (keys constraint-map)
                          param-names)
        in-clause (conj param-names '$)
        final-clause (concat [:find '?e]
                             [:in] in-clause
                             [:where] where-clause)]

    (println "FINAL clause > two > " final-clause)
    (partial apply d/q final-clause (d/db conn))))

(defn retrieve [conn constraint-map]

  (let [the-db (d/db conn)

        try-main (retrieve-entity conn constraint-map)
        try-one (retrieve-entity-1 conn constraint-map)
        try-two (retrieve-entity-2 conn constraint-map)








        ;;try-main (println "Try MAIN > " (retrieve-entity conn constraint-map))
        ;;try-one (println "Try 1 > " (retrieve-entity-1 conn constraint-map))
        ;;try-two (println "Try 2 > " (retrieve-entity-2 conn constraint-map))

        id-set (hset-to-cset (retrieve-entity conn constraint-map))
        entity-set (map (fn [inp]
                          (vivify-datomic-entity the-db inp))
                        id-set)]
    entity-set))


(defn retrieve-by-id [conn eid]

  (let [result (d/q '[:find ?eid :in $ ?eid :where [?eid]] (d/db conn) eid)
        result-map (into {} (vivify-datomic-entity (d/db conn) (ffirst result)))

        eid (ffirst result)
        final-map (assoc result-map :db/id eid)]

    final-map))


(defn update [conn domain-key datom-map]

  {:pre [(keyword? domain-key)
         (map? datom-map)]}

  (let [
        one (str "plugin." (name domain-key) ".create")
        lookup-key (keyword one)

        ;; find the mapping
        mapping (find-mapping lookup-key)

        ;; insert mapped function & preamble
        mapped-fn (first mapping) ]


    (println "UPDATE datom > " datom-map)

    ;; transact to Datomic
    @(datomic.api/transact conn [datom-map])))

(defn delete [conn entity-id]

  {:pre [(-> conn nil? not)
         (-> entity-id nil? not)]}

  @(datomic.api/transact conn [[:db.fn/retractEntity entity-id]] ))
