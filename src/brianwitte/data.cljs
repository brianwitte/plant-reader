(ns brianwitte.data
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]))

(def api-base-url "http://localhost:3000/api")

(def ^:private default-request-opts
  {:with-credentials? false
   :headers {"Content-Type" "application/json"}})

(defn- handle-response
  "Handle API response with consistent error handling"
  [response error-msg]
  (if (= 200 (:status response))
    (:body response)
    (do
      (js/console.error error-msg response)
      {:error error-msg})))

(defn- api-get
  "Generic API GET request with consistent error handling"
  [endpoint error-msg & {:keys [log-request?] :or {log-request? false}}]
  (go
    (let [url (str api-base-url endpoint)]
      (when log-request?
        (js/console.log "Fetching from:" url))
      (let [response (<! (http/get url default-request-opts))]
        (when log-request?
          (js/console.log "Response status:" (:status response))
          (js/console.log "Response body:" (:body response)))
        (handle-response response error-msg)))))

;; Public API functions
(defn fetch-species
  "Fetch all species from the API"
  []
  (api-get "/species" "Failed to fetch species" :log-request? true))

(defn fetch-species-by-id
  "Fetch a specific species by ID"
  [id]
  (api-get (str "/species/" id) "Failed to fetch species"))

(defn fetch-categories
  "Fetch all categories from the API"
  []
  (api-get "/categories" "Failed to fetch categories"))

(defn search-by-category
  "Search species by category"
  [category]
  (api-get (str "/search/category/" (js/encodeURIComponent category)) 
           "Failed to search"))

(defn search-by-name
  "Search species by common name"
  [name]
  (api-get (str "/search/name/" (js/encodeURIComponent name))
           "Failed to search"))

;; For backward compatibility with existing code
(def data
  {:species []     ; Will be populated by API calls
   :categories []
   :loading? true})
