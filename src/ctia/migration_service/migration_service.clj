(ns ctia.migration-service.migration-service
  (:require [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol MigrationService
  (migrate [this migration-id]
    "udpate the settings, mappings and templates of given store"))

(tk/defservice migration-service
  "A service to manage migrations of stores."
  MigrationService
  [[:ConfigService get-in-config]
   [:StoreService all-stores]]
  (init [this context]
    (log/info "Initialising MigrationService")
    (clojure.pprint/pprint (all-stores))
    context)
  (start [this context]
    (log/info "Starting MigrationService")
    context)
  (stop [this context]
    (log/info "Stoping MigrationService")
    context)
  (migrate [this migration-id]
    (log/info "Starting migration: " migration-id)
    ))
