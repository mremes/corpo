(ns corpo.core
  (:require [clj-http.client :as client]
            [clojure.java.jdbc :as jdbc]
            [jsonista.core :as j]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [slingshot.slingshot :refer [try+]]
            [clj-time.format :as tf]
            [clj-time.core :as tc])
  (:import (java.sql SQLException)))


(def base-url "http://avoindata.prh.fi/bis/v1")

(defn start_params [start_date end_date]
  {"totalResults" true
   "maxResults" 10
   "resultsFrom" 0
   "companyForm" "OY"
   "companyRegistrationFrom" start_date
   "companyRegistrationTo" end_date})


(def db
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "main.db"})

(jdbc/db-do-commands
  db "
  CREATE TABLE IF NOT EXISTS Company
  (
  name TEXT,
  companyForm TEXT,
  registrationDate TEXT,
  businessId TEXT PRIMARY KEY,
  businessLine TEXT,
  businessLineCode TEXT,
  addresses TEXT,
  phoneNumbers TEXT
  )
  ")

(def links (ref #{}))

(defn reset-state! [r val] (dosync (ref-set r val)))

(def mapper
  (j/object-mapper
    {:encode-key-fn str
     :decode-key-fn keyword}))

(defn make-request
  [url]
    (try+ (update (client/get url) :body #(j/read-value %1 mapper)) (catch [:status 503] {:keys []} (Thread/sleep (+ 3000 (rand-int 10000))) (make-request url))))


(defn is-fi?
  [x]
  (= "FI" (:language x)))

(defmacro =>
  [& body]
  `(fn [x#] (->> x# ~@body)))

(def doc_keys
  {:name :name
   :companyForm :companyForm
   :registrationDate :registrationDate
   :businessId :businessId
   :businessLine (=> :details :businessLines (filter is-fi?) first :name)
   :businessLineCode (=> :details :businessLines first :code)
   :addresses (=> :details :addresses (filter is-fi?) (map #(into {} (filter second (select-keys %1 [:street :postCode :city])))) dedupe)
   :phoneNumbers (=> :details :contactDetails (filter is-fi?) (filter #(or (= "Matkapuhelin" (:type %1)) (= "Puhelin" (:type %1)))) (map :value) dedupe)})

(defn fetch-company-data
  [link]
  (let [response (make-request (:detailsUri link))]
      (reduce-kv #(let [res (-> (assoc link :details (first (:results (:body response)))) %3)] (if (or (empty? res) (nil? res)) %1 (assoc %1 %2 res))) {} doc_keys)))

(defn inc-date [d]
  (let [fmt (tf/formatter "yyyy-MM-dd")]
  (tf/unparse fmt (tc/plus (tf/parse fmt d) (tc/days 1)))))

(defn get-url
  [date]
  (str base-url "?" (client/generate-query-string (start_params date (inc-date date)))))

(defn do-stuff [_start_date _end_date _times]
  (loop [cur_date _start_date
         url nil
         times 0]
    (if (or (= times _times) (= cur_date _end_date)) (do)
      (let [url (if (nil? url) (get-url cur_date) url)
            response (make-request url)
            body (:body response)
            c (async/to-chan (->> body :results))
            res (async/map fetch-company-data [c])]
        (async/go-loop [x (async/<! res)]
          (if x
            (do
              (try (jdbc/insert! db :company x)
                   (catch SQLException e (if (str/includes? e "UNIQUE constraint failed") (do) (throw e))))
              (dosync (commute links conj x))
              (recur (async/<! res)))
            (async/close! c)))
        (let [next-uri (:nextResultsUri body)]
          (if (empty? next-uri) (recur (inc-date cur_date) nil (inc times))
                                (recur cur_date next-uri (inc times))))))))



(defn -main []
  (do-stuff "2019-12-01" "2019-12-02" 2)
  (spit "/Users/mattiremes/foo.txt" (str/join "\n" (map #(j/write-value-as-string %1) @links))))