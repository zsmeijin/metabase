(ns metabase.query-processor.middleware.async-wait
  "Middleware that limits the number of concurrent queries for each database.

  Each connected database is limited to a maximum of 15 simultaneous queries (configurable) using these methods; any
  additional queries will park the thread. Super-useful for writing high-performance API endpoints. Prefer these
  methods to the old-school synchronous versions.

  How is this achieved? For each Database, we'll maintain a channel that acts as a counting semaphore; the channel
  will initially contain 15 permits. Each incoming request will asynchronously read from the channel until it acquires
  a permit, then put it back when it finishes."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [metabase
             [notification-center :as notifications]
             [util :as u]]
            [metabase.models.setting :refer [defsetting]]
            [metabase.util.i18n :refer [trs]])
  (:import [java.util.concurrent Executors ExecutorService]))

(defsetting max-simultaneous-queries-per-db
  (trs "Maximum number of simultaneous queries to allow per connected Database.")
  :type    :integer
  :default 15)

(defonce ^:private db-thread-pools (atom {}))

(defonce ^:private db-thread-pool-lock (Object.))

(defn- db-thread-pool ^ExecutorService [database-or-id]
  (let [id (u/get-id database-or-id)]
    (or
     (@db-thread-pools id)
     (locking db-thread-pool-lock
       (or
        (@db-thread-pools id)
        (log/debug (trs "Creating new query thread pool for Database {0}" id))
        (let [new-pool (Executors/newFixedThreadPool (max-simultaneous-queries-per-db))]
          (swap! db-thread-pools assoc id new-pool)
          new-pool))))))

(defn destroy-thread-pool! [database-or-id]
  (let [id (u/get-id database-or-id)]
    (locking db-thread-pool-lock
      (let [[{^ExecutorService thread-pool id}] (swap-vals! db-thread-pools dissoc id)]
        (when thread-pool
          (log/debug (trs "Destroying query thread pool for Database {0}" id))
          (.shutdownNow thread-pool))))))

(notifications/defobserver notifications/DatabaseDeleted
  [{db-id :id}]
  (destroy-thread-pool! db-id))

(defn wait-for-permit
  "Middleware that throttles the number of concurrent queries for each connected database, parking the thread until a
  permit becomes available."
  [qp]
  (fn [{database-id :database, :as query} respond raise canceled-chan]
    (let [futur (.submit (db-thread-pool database-id) ^Runnable #(qp query respond raise canceled-chan))]
      (a/go
        (when (a/<! canceled-chan)
          (log/debug (trs "Request canceled, canceling pending query"))
          (future-cancel futur))))
    nil))
