(ns ctia.test-helpers.generators.schemas.judgement-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [common :as schemas-common]
             [judgement :refer [Judgement NewJudgement]]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-judgement
  (gen/fmap
   (fn [[id disp]]
     (complete
      Judgement
      {:id id
       :disposition disp
       :disposition_name (get schemas-common/disposition-map disp)}))
   (gen/tuple
    (gen-id/gen-short-id-of-type :judgement)
    (gen/choose 1 5))))

(def gen-new-judgement
  (gen/fmap
   (fn [[id
         [disp-num disp-num? disp-name?]
         [start-time end-time]]]
     (complete
      NewJudgement
      (cond-> {}
        id
        (assoc :id id)

        disp-num?
        (assoc :disposition disp-num)

        disp-name?
        (assoc :disposition_name
               (get schemas-common/disposition-map disp-num))

        start-time
        (assoc-in [:valid_time :start_time] start-time)

        end-time
        (assoc-in [:valid_time :end_time] end-time))))
   (gen/tuple
    (maybe (gen-id/gen-short-id-of-type :judgement))
    (gen/tuple (gen/choose 1 5)
               gen/boolean
               gen/boolean)
    ;; complete doesn't seem to generate :valid_time values, so do it manually
    common/gen-valid-time-tuple)))
