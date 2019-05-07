(ns metabase.notification-center
  "Like NSNotificationCenter in macOS/iOS. A place keep all the stuff you can publish & subscribe to.

  TODO - I dont' think we need both this and `metabase.events`."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [metabase.util
             [cron :as cron-util]
             [i18n :refer [trs]]]
            [metabase.util.schema :as su]
            [schema.core :as s]))

(defonce ^:private metabase-notifications-channel (a/chan))

(defonce ^:private metabase-notifications-pub (a/pub metabase-notifications-channel ::event))

(def ^:private ^{:arglists '([topic ch])} sub
  (partial a/sub metabase-notifications-pub))

(defmulti validate-notification-schema
  "Define a schema to validate notifications of a given type before they are posted."
  {:arglists '([event])}
  identity)

(defmethod validate-notification-schema :default [_] nil)

(defmacro defnotification
  "Define a new notification."
  ([notification-name docstring]
   `(defnotification ~notification-name ~docstring nil))

  ([notification-name docstring schema]
   (let [kw (keyword (str (ns-name *ns*)) (str notification-name))]
     `(do
        (def ~notification-name ~docstring ~kw)
        ~(when (some? schema)
           `(defmethod validate-notification-schema ~kw [~'_] ~schema))))))

(defn post!
  ([event]
   (post! event nil))

  ([event notification]
   (when-let [schema (validate-notification-schema event)]
     (s/validate schema notification))
   (a/put! metabase-notifications-channel (assoc notification ::event event))))

(defn observe! [event f]
  (let [chan (a/chan)]
    (sub event chan)
    (a/go-loop []
      (when-let [notification (a/<! chan)]
        (try
          (f notification)
          (catch Throwable e
            (log/error e (trs "Error observing {0}" event))))
        (recur)))
    chan))

(defmacro defobserver [event [notification-binding] & body]
  (let [symb (-> (symbol (str "observer-" (hash &form)))
                 (vary-meta assoc :private true))]
    `(defonce ~symb
       (observe! ~event (fn [~(or notification-binding '_)]
                          ~@body)))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                             Various Notifications                                              |
;;; +----------------------------------------------------------------------------------------------------------------+

(defnotification DatabaseDeleted
  "Notification sent when a Database is deleted."
  {:id       su/IntGreaterThanZero
   s/Keyword s/Any})
