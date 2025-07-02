(ns brianwitte.data
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]))

(def api-base-url "http://localhost:3000/api")

(defn fetch-species
  "Fetch all species from the API"
  []
  (go
    (js/console.log "Fetching species from:" (str api-base-url "/species"))
    (let [response (<! (http/get (str api-base-url "/species")
                                 {:with-credentials? false
                                  :headers {"Content-Type" "application/json"}}))]
      (js/console.log "Response status:" (:status response))
      (js/console.log "Response body:" (:body response))
      (if (= 200 (:status response))
        (:body response)
        (do
          (js/console.error "Failed to fetch species:" response)
          {:error "Failed to fetch species"})))))

(defn fetch-species-by-id
  "Fetch a specific species by ID"
  [id]
  (go
    (let [response (<! (http/get (str api-base-url "/species/" id)
                                 {:with-credentials? false
                                  :headers {"Content-Type" "application/json"}}))]
      (if (= 200 (:status response))
        (:body response)
        (do
          (js/console.error "Failed to fetch species:" response)
          {:error "Failed to fetch species"})))))

(defn fetch-categories
  "Fetch all categories from the API"
  []
  (go
    (let [response (<! (http/get (str api-base-url "/categories")
                                 {:with-credentials? false
                                  :headers {"Content-Type" "application/json"}}))]
      (if (= 200 (:status response))
        (:body response)
        (do
          (js/console.error "Failed to fetch categories:" response)
          {:error "Failed to fetch categories"})))))

(defn search-by-category
  "Search species by category"
  [category]
  (go
    (let [response (<! (http/get (str api-base-url "/search/category/" (js/encodeURIComponent category))
                                 {:with-credentials? false
                                  :headers {"Content-Type" "application/json"}}))]
      (if (= 200 (:status response))
        (:body response)
        (do
          (js/console.error "Failed to search by category:" response)
          {:error "Failed to search"})))))

(defn search-by-name
  "Search species by common name"
  [name]
  (go
    (let [response (<! (http/get (str api-base-url "/search/name/" (js/encodeURIComponent name))
                                 {:with-credentials? false
                                  :headers {"Content-Type" "application/json"}}))]
      (if (= 200 (:status response))
        (:body response)
        (do
          (js/console.error "Failed to search by name:" response)
          {:error "Failed to search"})))))

;; For backward compatibility with existing code
(def data
  {:species []  ; Will be populated by API calls
   :categories []
   :loading? true})
