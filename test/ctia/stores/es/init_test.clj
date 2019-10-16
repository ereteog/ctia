(ns ctia.stores.es.init-test
  (:require [ctia.stores.es.init :as sut]
            [clj-http.client :as http]
            [clj-momo.lib.es
             [index :as index]
             [document :as doc]
             [conn :as conn]]
            [clojure.test :refer [deftest testing is]]))

(def es-conn (conn/connect {:host "localhost"
                            :port "9200"}))
(def indexname "ctia_init_test_sighting")
(def write-alias (str indexname "-write"))
(def props-aliased {:entity :sighting
                    :indexname indexname
                    :shards 2
                    :replicas 1
                    :mappings {:a 1 :b 2}
                    :host "localhost"
                    :port 9200
                    :aliased true})

(def props-not-aliased {:entity :sighting
                        :indexname indexname
                        :shards 2
                        :replicas 1
                        :mappings {:a 1 :b 2}
                        :host "localhost"
                        :port 9200
                        :aliased false})

(deftest init-store-conn-test
  (testing "init store conn should return a proper conn state with unaliased conf"
    (let [{:keys [index props config conn]}
          (sut/init-store-conn props-not-aliased)]
      (is (= index indexname))
      (is (= (:write-index props) indexname))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (nil? (:aliases config)))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (testing "init store conn should return a proper conn state with unaliased conf"
    (let [{:keys [index props config conn]}
          (sut/init-store-conn props-aliased)]
      (is (= index indexname))
      (is (= (:write-index props) write-alias))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (= indexname
             (-> config :aliases keys first)))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b]))))))

(deftest init-es-conn!-test
  (index/delete! es-conn (str indexname "*"))
  (testing "init-es-conn! should return a proper conn state with unaliased conf, but not create any index"
    (let [{:keys [index props config conn]}
          (sut/init-es-conn! props-not-aliased)
          existing-index (index/get es-conn (str indexname "*"))]
      (is (empty? existing-index))
      (is (= index indexname))
      (is (= (:write-index props) indexname))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (nil? (:aliases config)))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (testing "init-es-conn! should return a proper conn state with aliased conf, and create an initial aliased index"
    (index/delete! es-conn (str indexname "*"))
    (let [{:keys [index props config conn]}
          (sut/init-es-conn! props-aliased)
          existing-index (index/get es-conn (str indexname "*"))
          created-aliases (->> existing-index
                               vals
                               first
                               :aliases
                               keys
                               set)]
      (is (= #{(keyword indexname) (keyword write-alias)}
             created-aliases))
      (is (= index indexname))
      (is (= (:write-index props) write-alias))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (= indexname
             (-> config :aliases keys first)))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (testing "init-es-conn! should return a conn state that ignore aliased conf setting when an unaliased index already exists"
    (index/delete! es-conn (str indexname "*"))
    (http/delete (str "http://localhost:9200/_template/" indexname "*"))
    (index/create! es-conn indexname {})
    (let [{:keys [index props config conn]}
          (sut/init-es-conn! props-aliased)
          existing-index (index/get es-conn (str indexname "*"))
          created-aliases (->> existing-index
                               vals
                               first
                               :aliases
                               keys
                               set)]
      (is (= #{} created-aliases))
      (is (= index indexname))
      (is (= (:write-index props) indexname))
      (is (= "http://localhost:9200" (:uri conn)))
      (is (= indexname
             (-> config :aliases keys first)))
      (is (= 1 (get-in config [:settings :number_of_replicas])))
      (is (= 2 (get-in config [:settings :number_of_shards])))
      (is (= {} (select-keys (:mappings config) [:a :b])))))

  (http/delete (str "http://localhost:9200/_template/" indexname "*"))
  (index/delete! es-conn (str indexname "*")))
