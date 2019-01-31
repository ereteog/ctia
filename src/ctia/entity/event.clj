(ns ctia.entity.event
  (:require
   [ctia.entity.event.store
    :refer [->EventStore]]
   [compojure.api.sweet :refer [GET routes]]
   [ring.util.http-response :refer [ok]]
   [schema-tools.core :as st]
   [schema.core :as s]
   [clj-momo.lib.clj-time.core :as t]
   [clj-momo.lib.es
    [document :as d]
    [schemas :refer [ESConnState SliceProperties]]
    [slice :refer [get-slice-props]]]
   [ctia.entity.event.schemas
    :refer [Event PartialEvent PartialEventList EventBucket]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.lib.pagination :refer [list-response-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es
    [crud :as crud]
    [mapping :as em]]
   [ctia.store :refer [read-store list-events list-all-pages]]
   [ctia.domain.entities :as ent]
   [ctia.properties :refer [properties]]
   [clojure.set :as set]))

(def event-mapping
  {"event"
   {:dynamic false
    :properties
    {:owner em/token
     :groups em/token
     :tlp em/token
     :timestamp em/ts
     :entity {:type "object"
              :properties {:id em/token
                           :source_ref em/token
                           :target_ref em/token}}
     :id em/token
     :http-params {:enabled false
                   :type "object"}
     :type em/token
     :fields {:enabled false
              :type "object"}}}})

(def event-fields
  [:owner
   :groups
   :timestamp
   :id
   :entity.id
   :entity.type
   :event_type])

(def event-sort-fields
  (apply s/enum event-fields))

(s/defschema EventFieldsParam
  {(s/optional-key :fields) [event-sort-fields]})

(s/defschema EventSearchParams
  (st/merge
   {:query s/Str}
   PagingParams
   BaseEntityFilterParams
   EventFieldsParam))

(def EventGetParams EventFieldsParam)

(defn same-bucket? [bucket event]
  (let [max-seconds (get-in @properties [:ctia :events :timeline :max-seconds] 5)
        from        (t/minus (:from bucket) (t/seconds max-seconds))
        to          (t/plus (:to bucket) (t/seconds max-seconds))]
    (and (= (:owner bucket) (:owner event))
         (t/before? from (:timestamp event))
         (t/before? (:timestamp event) to))))

(defn init-bucket [event]
  {:count 1
   :owner (:owner event)
   :from (:timestamp event)
   :to (:timestamp event)
   :events (list event)})

(defn min-ts [t1 t2]
  (if (t/before? t1 t2) t1 t2))

(defn max-ts [t1 t2]
  (if (t/before? t1 t2) t2 t1))

(defn bucket-append [bucket event]
  (-> (update bucket :count inc)
      (update :from min-ts (:timestamp event))
      (update :to max-ts (:timestamp event))
      (update :events conj event)))

(defn timeline-append [comp timeline event]
  (let [previous (first timeline)]
    (if (and (map? previous)
             (same-bucket? previous event))
        (cons (bucket-append previous event)
              (rest timeline))
        (cons (init-bucket event) timeline))))

(defn bucketize-events
  [events comp]
  (let [events (sort-by (juxt :owner :timestamp :event_type) events)
        buckets (reduce (partial timeline-append comp) [] events)]
    (reverse (sort-by :from buckets))))

(defn fetch-related-events [_id identity-map q]
  (mapcat #(some-> (list-all-pages :event
                                   list-events
                                   {% _id}
                                   identity-map
                                   q)
                   ent/un-store-all
                   set)
          [:entity.id :entity.source_ref :entity.target_ref]))

(def event-history-routes
  (routes
   (GET "/history/:entity_id" []
        :return [EventBucket]
        :path-params [entity_id :- s/Str]
        :header-params [{Authorization :- (s/maybe s/Str) nil}]
        :summary "Timeline history of an entity"
        :capabilities :search-event
        :auth-identity identity
        :identity-map identity-map
        (let [res (fetch-related-events entity_id identity-map {})
              timeline (bucketize-events res same-bucket?)]
          (ok timeline)))))

(def event-routes
  (routes
   event-history-routes
   (entity-crud-routes
    {:tags ["Event"]
     :entity :event
     :entity-schema Event
     :get-schema PartialEvent
     :get-params EventGetParams
     :list-schema PartialEventList
     :search-schema PartialEventList
     :search-q-params EventSearchParams
     :get-capabilities :read-event
     :can-update? false
     :can-patch? false
     :can-post? false
     :can-get-by-external-id? false
     :search-capabilities :search-event})))

(def event-entity
  {:route-context "/event"
   :tags ["Event"]
   :schema Event
   :stored-schema Event
   :partial-schema PartialEvent
   :partial-stored-schema PartialEvent
   :partial-list-schema PartialEventList
   :no-bulk? true
   :entity :event
   :plural :events
   :es-store ->EventStore
   :es-mapping event-mapping
   :routes event-routes})
