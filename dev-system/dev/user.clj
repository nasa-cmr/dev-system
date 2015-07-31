(ns user
  (:require [cmr.common.dev.capture-reveal]
            [clojure.main]
            [debugger.core]
            [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.dev-system.system :as system]
            [cmr.dev-system.tests :as tests]
            [cmr.common.concepts :as concepts]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.dev.util :as d]
            [cmr.system-int-test.system :as sit-sys]
            [cmr.common.jobs :as jobs]
            [cmr.common.config :as config]
            [earth.driver :as earth-viz]
            [common-viz.util :as common-viz]
            [vdd-core.core :as vdd])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(defonce system nil)

(defn start
  "Starts the current development system."
  []
  (config/reset-config-values)

  (jobs/set-default-job-start-delay! (* 3 3600))

  ;; Comment/uncomment these lines to switch between external and internal settings.

  (system/set-dev-system-elastic-type! :in-memory)
  ; (system/set-dev-system-elastic-type! :external)

  ;; Note external ECHO does not work with the automated tests. The automated tests expect they
  ;; can interact with the Mock ECHO to setup users, acls, and other ECHO objects.
  (system/set-dev-system-echo-type! :in-memory)
  ; (system/set-dev-system-echo-type! :external)

  (system/set-dev-system-db-type! :in-memory)
  ; (system/set-dev-system-db-type! :external)

  (system/set-dev-system-message-queue-type! :in-memory)
  ; (system/set-dev-system-message-queue-type! :external)

  (let [s (system/create-system)]
    (alter-var-root #'system
                    (constantly
                      (system/start s))))
  (d/touch-user-clj))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s]
                    (when s (system/stop s)))))

(defn reset []
  ;; Stop the system integration test system
  (sit-sys/stop)
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))


(defn reload-coffeescript []
  (do
    (println "Compiling coffeescript")
    (println (common-viz/compile-coffeescript (get-in system [:components :vdd-server :config])))
    (vdd/data->viz {:cmd :reload})))

(defn run-all-tests-future
  "Runs all tests asynchronously, with :fail-fast? and :speak? enabled."
  []
  (future
    (tests/run-all-tests {:fail-fast? true :speak? true})))

;;; Use these functions at the REPL for fun and profit. Soon you won't
;;; want to do anything else.

;; this is a function so that you can load the code while (reset)
;; is running
(defn get-db [] (-> system :apps :metadata-db :db))

(defn get-ingest [] (-> system :apps :ingest))

(defn get-providers
  []
  (cmr.metadata-db.data.providers/get-providers (get-db)))

(defn get-provider
  [id]
  (cmr.metadata-db.data.providers/get-provider (get-db) id))

(defn to-provider
  [x]
  (if (string? x)
    (get-provider x)
    x))

(defn find-concepts
  [provider params]
  (cmr.metadata-db.data.concepts/find-concepts (get-db) [(to-provider provider)] params))

(defn get-concept
  [concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (last (find-concepts provider-id {:concept-id concept-id
                                      :concept-type concept-type}))))

(defn save-granule
  [granule]
  (cmr.ingest.services.ingest-service/save-granule {:system (get-ingest)} granule))

(defn find-concepts
  [params]
  (cmr.metadata-db.services.search-service/find-concepts
   {:system (-> system :apps :metadata-db)}
   params))

(info "Custom dev-system user.clj loaded.")
