(ns stefon-datomic.plugin-spec

  (:require [speclj.core :refer :all]
            [datomic.api :as datomic]
            [clojure.java.io :as io]

            [stefon.shell :as shell]
            [stefon.domain :as domain]
            [stefon.shell.plugin :as plugin]
            [stefon-datomic.plugin :as pluginD]
            [stefon-datomic.crud :as crud]))


(def config (load-string (slurp (io/resource "stefon-datomic.edn"))))
(def domain-schema {:posts
                    [{:name :id :cardinality :one :type :string}
                     {:name :title :cardinality :one :type :string}
                     {:name :content :cardinality :one :type :string}
                     {:name :content-type :cardinality :one :type :string}
                     {:name :created-date :cardinality :one :type :instant}
                     {:name :modified-date :cardinality :one :type :instant}
                     {:name :assets :cardinality :many :type :ref}
                     {:name :tags :cardinality :many :type :ref}],
                    :assets
                    [{:name :id :cardinality :one :type :string}
                     {:name :name :cardinality :one :type :string}
                     {:name :type :cardinality :one :type :string}
                     {:name :asset :cardinality :one :type :string}],
                    :tags
                    [{:name :id :cardinality :one :type :string}
                     {:name :name :cardinality :one :type :string}]})


(describe "[SEPC] Plugin should be able to attach to a running Stefon instance => "

            (before (datomic/delete-database (-> config :dev :url))
                  (shell/stop-system))


            (it "Testing the core plugin function"

              (let [result (atom nil)
                    tee-fn (fn [msg]

                             (println "<< RECIEVEING Message >> " msg)
                             (swap! result (fn [inp] msg)))

                    step-one (shell/start-system)
                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)
                    step-three (pluginD/add-receive-tee tee-fn)]


                ;; create a post, then check the DB
                (shell/create :post "t" "c" "c/t" "0000" "1111" nil nil)

                ;;(println "... " result)
                (should-not-be-nil @result)
                (should (map? @result))
                (should= :plugin.post.create (-> @result keys first))
                (should= {:title "t" :content "c" :content-type "c/t" :created-date "0000" :modified-date "1111" :assets nil :tags nil}
                         (-> @result :plugin.post.create :message :stefon.post.create :parameters (dissoc :id))))) )


(describe "[SPEC] Integrate CRUD with plugin messages > CREATE"

          (it "Testing kernel / plugin connection with CREATE"

              (shell/stop-system)
              (let [
                    result (pluginD/bootstrap-stefon {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin})
                    conn (:conn result)

                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)

                    create-promise (promise)
                    retrieve-promise (promise)

                    step-four (pluginD/subscribe-to-braodcast (fn [msg]

                                                                (println "<< IN broadcast > 1 >>" msg)

                                                                (deliver create-promise (-> msg :plugin.post.create :message))
                                                                (deliver retrieve-promise (crud/retrieve conn :post {:title "my post"})) ))

                    date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013")) ]

                ;; CREATE Post
                (shell/create :post "my post" "my content" "text/md" date-one date-one [] [])

                (should-not-be-nil @retrieve-promise)
                (should-not (empty? @retrieve-promise))
                (should= 1 (count @retrieve-promise)) )))

(describe "[SPEC] Integrate CRUD with plugin messages > RETRIEVE"

          (it "Testing kernel / plugin connection with RETRIEVE"


              (shell/stop-system)
              (let [
                    result (pluginD/bootstrap-stefon {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin})
                    conn (:conn result)


                    ;; initialize datomic plugin
                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)

                    ;; separate test plugin
                    test-retrieved (promise)
                    step-three (promise)
                    xx (deliver step-three (shell/attach-plugin (fn [msg]

                                                                  ;; send a retrieve command
                                                                  (if (-> msg :result :tempids)

                                                                    ((:sendfn @step-three) {:id (:id @step-three)
                                                                                            :message {:stefon.post.retrieve {:parameters {:id (-> msg :result :tempids vals first)}}}}) )

                                                                  ;; evaluate retrieve results
                                                                  (if (some #{:posts/modified-date} (-> msg :result keys))

                                                                    (deliver test-retrieved (:result msg))) )))

                    date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013")) ]


                ;; kickoff the send process
                ((:sendfn @step-three) {:id (:id @step-three)
                                        :message {:stefon.post.create
                                                  {:parameters {:title "my post" :content "my content" :content-type "text/md" :created-date date-one :modified-date date-one :assets [] :tags []}} }})

                (should-not-be-nil @test-retrieved)
                (should (some #{:db/id :posts/id :posts/title :posts/content :posts/content-type :posts/created-date :posts/modified-date} (keys @test-retrieved)))


                )))

(describe "[SPEC] Integrate CRUD with plugin messages > UPDATE"

          (it "Testing kernel / plugin connection with UPDATE"


              (shell/stop-system)
              (let [
                    result (pluginD/bootstrap-stefon {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin})
                    conn (:conn result)


                    ;; initialize datomic plugin
                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)

                    ;; separate test plugin
                    test-retrieved (promise)
                    step-three (promise)
                    post-id-promise (promise)
                    post-did-promise (promise)
                    xx (deliver step-three (shell/attach-plugin (fn [msg]

                                                                  ;;(println "*** " msg)


                                                                  ;; GET the Post id
                                                                  (if (= "kernel" (:from msg))
                                                                    (deliver post-id-promise (-> msg :result :id)))


                                                                  ;; send an UPDATE command
                                                                  (if (-> msg :result :tempids empty? not)

                                                                    (do
                                                                      (deliver post-did-promise (-> msg :result :tempids vals first))
                                                                      ((:sendfn @step-three) {:id (:id @step-three)
                                                                                              :message {:stefon.post.update {:parameters {:id @post-id-promise
                                                                                                                                          :param-map {:title "new title"
                                                                                                                                                      :content "new content"}}}}})) )

                                                                  ;; retrieve AFTER update
                                                                  (if (and (-> msg :result :tempids empty?)
                                                                           (realized? post-did-promise)
                                                                           (= :stefon.post.update (-> msg :action)))

                                                                    ((:sendfn @step-three) {:id (:id @step-three)
                                                                                            :message {:stefon.post.retrieve {:parameters {:id @post-did-promise}}}}))


                                                                  ;; evaluate retrieve results
                                                                  (if (and (not= "kernel" (:id msg))
                                                                           (= :stefon.post.retrieve (:action msg))
                                                                           (not (nil? (:result msg))))

                                                                    (do
                                                                      (deliver test-retrieved (:result msg))

                                                                      ;;(println "... " msg)
                                                                      (should-not-be-nil @test-retrieved)
                                                                      (should (some #{:db/id :posts/id :posts/title :posts/content :posts/content-type :posts/created-date :posts/modified-date}
                                                                                    (keys @test-retrieved)))

                                                                      )) )))

                    date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013")) ]


                ;; kickoff the send process
                ((:sendfn @step-three) {:id (:id @step-three)
                                        :message {:stefon.post.create
                                                  {:parameters {:title "my post" :content "my content" :content-type "text/md" :created-date date-one :modified-date date-one :assets [] :tags []}} }}) )))

(describe "[SPEC] Integrate CRUD with plugin messages > DELETE"

          (it "Testing kernel / plugin connection with DELETE"

              (shell/stop-system)
              (let [
                    result (pluginD/bootstrap-stefon {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin})
                    conn (:conn result)

                    ;; initialize datomic plugin
                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)

                    ;; separate test plugin
                    test-retrieved (promise)
                    step-three (promise)
                    post-id-promise (promise)
                    post-did-promise (promise)
                    xx (deliver step-three (shell/attach-plugin (fn [msg]

                                                                  ;;(println "*** " msg)

                                                                  ;; GET the Post id
                                                                  (if (= "kernel" (:from msg))
                                                                    (deliver post-id-promise (-> msg :result :id)))


                                                                  ;; send an DELETE command
                                                                  (if (and (not= "kernel" (:from msg))
                                                                           (= :stefon.post.create (:action msg))
                                                                           (-> msg :result :tempids empty? not))

                                                                    (let [did (-> msg :result :tempids vals first)]

                                                                      (deliver post-did-promise did)
                                                                      ((:sendfn @step-three) {:id (:id @step-three)
                                                                                              :message {:stefon.post.delete {:parameters {:id did}}}})) )

                                                                  ;; retrieve AFTER delete
                                                                  (if (and (not= "kernel" (:from msg))
                                                                           (= :stefon.post.delete (-> msg :action)))

                                                                      ((:sendfn @step-three) {:id (:id @step-three)
                                                                                              :message {:stefon.post.retrieve {:parameters {:id @post-did-promise}}}}))


                                                                  ;; evaluate retrieve results
                                                                  (if (and (not= "kernel" (:id msg))
                                                                             (= :stefon.post.retrieve (:action msg))
                                                                             (not (nil? (:result msg))))

                                                                      (do

                                                                        ;;(println "... " msg)
                                                                        (should-not-be-nil (:result msg))
                                                                        (should (empty? (:result msg))))))))

                    date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013")) ]


                ;; kickoff the send process
                ((:sendfn @step-three) {:id (:id @step-three)
                                        :message {:stefon.post.create
                                                  {:parameters {:title "my post" :content "my content" :content-type "text/md" :created-date date-one :modified-date date-one :assets [] :tags []}} }}) )))


(describe "[SPEC] Integrate CRUD with plugin messages > FIND"

          (it "Testing kernel / plugin connection with FIND"

              (shell/stop-system)
              (let [
                    result (pluginD/bootstrap-stefon {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin})
                    conn (:conn result)

                    ;; initialize datomic plugin
                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)

                    ;; separate test plugin
                    test-retrieved (promise)
                    step-three (promise)
                    post-id-promise (promise)
                    post-did-promise (promise)
                    xx (deliver step-three (shell/attach-plugin (fn [msg]

                                                                  ;; GET the Post id
                                                                  (if (= "kernel" (:from msg))
                                                                    (deliver post-id-promise (-> msg :result :id)))


                                                                  ;; send an FIND command
                                                                  (if (and (not= "kernel" (:from msg))
                                                                           (= :stefon.post.create (:action msg))
                                                                           (-> msg :result :tempids empty? not))

                                                                    (let [did (-> msg :result :tempids vals first)]

                                                                      (deliver post-did-promise did)
                                                                      ((:sendfn @step-three) {:id (:id @step-three)
                                                                                              :message {:stefon.post.find {:parameters {:param-map {:title "my post"}}}}})) )

                                                                  ;; retrieve AFTER find
                                                                  (if (and (not= "kernel" (:from msg))
                                                                           (= :stefon.post.find (-> msg :action)))

                                                                    ;; evaluate retrieve results
                                                                    (do
                                                                        (should-not-be-nil (:result msg))
                                                                        (should-not (empty? (:result msg)))))
                                                                  )))

                    date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013")) ]


                ;; kickoff the send process
                ((:sendfn @step-three) {:id (:id @step-three)
                                        :message {:stefon.post.create
                                                  {:parameters {:title "my post" :content "my content" :content-type "text/md" :created-date date-one :modified-date date-one :assets [] :tags []}} }}))) )

(describe "[SPEC] Integrate CRUD with plugin messages > CREATE with RELATIONSHIPS"

          (it "Testing kernel / plugin connection with CREATE with RELATIONSHIPS"

              (shell/stop-system)
              (let [
                    result (pluginD/bootstrap-stefon {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin})
                    conn (:conn result)

                    ;; initialize datomic plugin
                    step-two (pluginD/plugin {:system-started? shell/system-started? :start-system shell/start-system :attach-plugin shell/attach-plugin} :dev)

                    ;; separate test plugin
                    test-retrieved (promise)
                    step-three (promise)
                    post-id-promise (promise)
                    post-did-promise (promise)
                    xx (deliver step-three (shell/attach-plugin (fn [msg]

                                                                  ;; GET the Post id
                                                                  (if (= "kernel" (:from msg))
                                                                    (deliver post-id-promise (-> msg :result :id)))


                                                                  ;; send an FIND command
                                                                  (if (and (= :stefon.post.create-relationship (:action msg))
                                                                           (not= "kernel" (:from msg)))

                                                                    (do

                                                                      (should-not (empty? (-> msg :result :tempids)))
                                                                      (should= 3 (count (vals (-> msg :result :tempids))))
                                                                      )))))

                    date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013")) ]


                ;; kickoff the send process
                ((:sendfn @step-three) {:id (:id @step-three)
                                        :message {:stefon.post.create-relationship
                                                  {:parameters {:entity-list [(crud/add-entity-ns :posts {:id (str (java.util.UUID/randomUUID))
                                                                                                          :title "tree house"
                                                                                                          :content "c"
                                                                                                          :content-type "c/t"
                                                                                                          :created-date date-one
                                                                                                          :modified-date date-one
                                                                                                          })
                                                                              (crud/add-entity-ns :assets {:id (str (java.util.UUID/randomUUID)) :name "iss-orbit" :type "image/png" :asset "binarygoo"})
                                                                              (crud/add-entity-ns :tags {:id (str (java.util.UUID/randomUUID)) :name "datomic"})]}
                                                   } }}))))
