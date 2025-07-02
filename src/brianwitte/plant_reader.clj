(ns brianwitte.plant-reader
  (:require [clojure.java.jdbc :as jdbc]
            [reitit.ring :as reitit-ring]
            [reitit.http :as http]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response]
            [ring.util.codec :as codec]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as state])
  (:gen-class))


(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

;; ---- Database Connection ----
(def ^:dynamic *db-connection* 
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "plants.db"})

(defn load-database []
  "Initialize database connection (SQLite auto-connects)"
  *db-connection*)

;; ---- Database Schema ----
(def species-table-schema
  "CREATE TABLE IF NOT EXISTS species (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    common_name VARCHAR(100),
    category VARCHAR(50),
    scientific_name VARCHAR(150),
    pronunciation VARCHAR(200),
    type VARCHAR(100),
    height VARCHAR(50),
    spread VARCHAR(50),
    spacing VARCHAR(50),
    habit TEXT,
    culture TEXT,
    uses TEXT,
    problems TEXT,
    notes TEXT)")

(defn ensure-table-exists []
  "Create species table if it doesn't exist"
  (try
    (jdbc/execute! (load-database) [species-table-schema])
    (catch Exception e
      (println "Table creation failed:" (.getMessage e)))))

;; ---- Core Database Functions ----
(defn list-all-species []
  "Get all species from database"
  (try
    (jdbc/query (load-database) ["SELECT * FROM species ORDER BY common_name"])
    (catch Exception e
      (println "Error listing species:" (.getMessage e))
      [])))

(defn find-species-by-id [id]
  "Find species by ID"
  (try
    (first (jdbc/query (load-database) 
                       ["SELECT * FROM species WHERE id = ?" (Integer/parseInt (str id))]))
    (catch Exception e
      (println "Error finding species by ID:" (.getMessage e))
      nil)))

(defn search-by-category [category]
  "Search species by category (case-insensitive)"
  (try
    (let [all-species (list-all-species)]
      (filter #(and (:category %)
                    (str/includes? (str/lower-case (:category %)) 
                                   (str/lower-case category)))
              all-species))
    (catch Exception e
      (println "Error searching by category:" (.getMessage e))
      [])))

(defn search-by-common-name [name]
  "Search species by common name (case-insensitive)"
  (try
    (let [all-species (list-all-species)]
      (filter #(and (:common_name %)
                    (str/includes? (str/lower-case (:common_name %)) 
                                   (str/lower-case name)))
              all-species))
    (catch Exception e
      (println "Error searching by name:" (.getMessage e))
      [])))

(defn get-categories []
  "Get distinct categories"
  (try
    (map :category (jdbc/query (load-database) 
                               ["SELECT DISTINCT category FROM species ORDER BY category"]))
    (catch Exception e
      (println "Error getting categories:" (.getMessage e))
      [])))

;; ---- Utility Functions ----
(defn species-to-map [species]
  "Convert species database record to map for JSON serialization"
  (when species
    {:id (:id species)
     :common-name (:common_name species)
     :category (:category species)
     :scientific-name (:scientific_name species)
     :pronunciation (:pronunciation species)
     :type (:type species)
     :height (:height species)
     :spread (:spread species)  
     :spacing (:spacing species)
     :habit (:habit species)
     :culture (:culture species)
     :uses (:uses species)
     :problems (:problems species)
     :notes (:notes species)}))

(defn error-response 
  ([message] (error-response message 400))
  ([message status-code]
   {:status status-code
    :body {:error message}}))

;; ---- Static File Serving ----
(defn serve-file [file-path content-type]
  "Serve a static file with appropriate headers"
  (let [file (io/file file-path)]
    (if (.exists file)
      {:status 200
       :headers {"Content-Type" content-type}
       :body file}
      {:status 404
       :body "File not found"})))

;; ---- Print / Export Functions ----
(defn print-species [species]
  "Print species information to console"
  (println)
  (println (format "%s (%s), %s"
                   (:common_name species)
                   (:scientific_name species)
                   (:category species)))
  (doseq [[field value] [[:pronunciation (:pronunciation species)]
                         [:type (:type species)]
                         [:height (:height species)]
                         [:spread (:spread species)]
                         [:spacing (:spacing species)]
                         [:habit (:habit species)]
                         [:culture (:culture species)]
                         [:uses (:uses species)]
                         [:problems (:problems species)]
                         [:notes (:notes species)]]
          :when (and value (not= value ""))]
    (println (format "%s: %s" (str/capitalize (name field)) value))))

(defn export-all-json []
  "Export all species as JSON string"
  (m/encode "application/json" (map species-to-map (list-all-species))))

;; ---- Route Handlers ----
(defn handle-api-species [{{:keys [limit offset]} :query-params}]
  "Handle GET /api/species with optional pagination"
  (try
    (let [limit-num (when limit (Integer/parseInt limit))
          offset-num (when offset (Integer/parseInt offset))
          all-species (list-all-species)
          total-count (count all-species)
          offset-val (or offset-num 0)
          species-subset (if limit-num
                           (->> all-species
                                (drop offset-val)
                                (take limit-num))
                           all-species)]
      {:status 200
       :body {:total total-count
              :count (count species-subset)
              :offset offset-val
              :species (map species-to-map species-subset)}})
    (catch Exception e
      (error-response (str "Failed to retrieve species: " (.getMessage e)) 500))))

(defn handle-api-species-by-id [{{:keys [id]} :path-params}]
  "Handle GET /api/species/:id"
  (try
    (if-let [species (find-species-by-id id)]
      {:status 200
       :body (species-to-map species)}
      (error-response "Species not found" 404))
    (catch Exception e
      (error-response (str "Invalid species ID or database error: " (.getMessage e)) 400))))

(defn handle-api-categories [_]
  "Handle GET /api/categories"
  (try
    {:status 200
     :body {:categories (get-categories)}}
    (catch Exception e
      (error-response (str "Failed to retrieve categories: " (.getMessage e)) 500))))

(defn handle-api-search-category [{{:keys [category]} :path-params}]
  "Handle GET /api/search/category/:category"
  (try
    (let [decoded-category (codec/url-decode category)
          species (search-by-category decoded-category)]
      {:status 200
       :body {:category decoded-category
              :count (count species)
              :species (map species-to-map species)}})
    (catch Exception e
      (error-response (str "Search failed: " (.getMessage e)) 500))))

(defn handle-api-search-name [{{:keys [name]} :path-params}]
  "Handle GET /api/search/name/:name"
  (try
    (let [decoded-name (codec/url-decode name)
          species (search-by-common-name decoded-name)]
      {:status 200
       :body {:search-term decoded-name
              :count (count species)
              :species (map species-to-map species)}})
    (catch Exception e
      (error-response (str "Search failed: " (.getMessage e)) 500))))

(defn handle-api-health [_]
  "Handle GET /api/health"
  (try
    (load-database)
    {:status 200
     :body {:status "healthy"
            :database "connected"
            :timestamp (System/currentTimeMillis)}}
    (catch Exception e
      (error-response (str "Health check failed: " (.getMessage e)) 500))))

(defn handle-api-root [_]
  "Handle GET /api - API documentation"
  {:status 200
   :body {:message "Plant Reader API"
          :version "1.0"
          :endpoints {:species "/api/species - List all species (supports ?limit=N&offset=N)"
                      :species-by-id "/api/species/:id - Get species by ID"
                      :categories "/api/categories - List all categories"
                      :search-category "/api/search/category/:category - Search by category"
                      :search-name "/api/search/name/:name - Search by common name"
                      :health "/api/health - Health check"}}})

;; ---- Static File Handlers ----
(defn handle-index [_]
  (serve-file "index.html" "text/html"))

(defn handle-app-js [_]
  (serve-file "src/app.js" "application/javascript"))

(defn handle-static [{{:keys [*]} :path-params}]
  (serve-file (str "static/" *) "application/octet-stream"))

;; ---- Router Configuration ----
(def router
  (reitit-ring/router
    [["/" {:get handle-index}]
     ["/src/app.js" {:get handle-app-js}]
     ["/static/*" {:get handle-static}]
     ["/api"
      ["" {:get handle-api-root}]
      ["/health" {:get handle-api-health}]
      ["/species" {:get handle-api-species}]
      ["/species/:id" {:get handle-api-species-by-id}]
      ["/categories" {:get handle-api-categories}]
      ["/search"
       ["/category/:category" {:get handle-api-search-category}]
       ["/name/:name" {:get handle-api-search-name}]]]]
    {:exception pretty/exception
     :data {:muuntaja m/instance
            :middleware [;; swagger feature
                         swagger/swagger-feature
                         ;; query-params & form-params
                         parameters/parameters-middleware
                         ;; content-negotiation
                         muuntaja/format-negotiate-middleware
                         ;; encoding response body
                         muuntaja/format-response-middleware
                         ;; exception handling
                         exception/exception-middleware
                         ;; decoding request body
                         muuntaja/format-request-middleware
                         ;; multipart
                         multipart/multipart-middleware]}}))

;; ---- Application Configuration ----
(def app
  (reitit-ring/ring-handler
    router
    (reitit-ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/api-docs"
         :config {:validatorUrl nil
                  :operationsSorter "alpha"}})
      (reitit-ring/create-default-handler))
    {:middleware [[wrap-cors
                   :access-control-allow-origin [#".*"]
                   :access-control-allow-methods [:get :post :put :delete :options]
                   :access-control-allow-headers ["Content-Type"]]]}))

;; ---- Integrant Configuration ----
(def system-config
  {:plant-reader/database {}
   :plant-reader/server {:port 8080
                         :handler (ig/ref :plant-reader/handler)}
   :plant-reader/handler {:database (ig/ref :plant-reader/database)}})

;; ---- Integrant Methods ----
(defmethod ig/init-key :plant-reader/database [_ _]
  (println "Initializing database...")
  (ensure-table-exists)
  (load-database))

(defmethod ig/init-key :plant-reader/handler [_ {:keys [database]}]
  (println "Initializing handler...")
  app)

(defmethod ig/init-key :plant-reader/server [_ {:keys [port handler]}]
  (println (str "Starting server on port " port "..."))
  (let [server (jetty/run-jetty handler {:port port :join? false})]
    (println)
    (println (str "Plant Reader Server started on port " port))
    (println)
    (println (str "Web Application: http://localhost:" port))
    (println (str "API Documentation: http://localhost:" port "/api-docs"))
    (println)
    (println "API endpoints:")
    (println "  GET  /api                     - API documentation")
    (println "  GET  /api/health              - Health check")
    (println "  GET  /api/species             - List all species")
    (println "  GET  /api/species/:id         - Get species by ID")
    (println "  GET  /api/categories          - List categories")
    (println "  GET  /api/search/category/:cat - Search by category")
    (println "  GET  /api/search/name/:name   - Search by name")
    (println)
    (println "Static files served from project directory")
    server))

(defmethod ig/halt-key! :plant-reader/server [_ server]
  (println "Stopping server...")
  (.stop server))

(defmethod ig/halt-key! :plant-reader/database [_ _]
  (println "Closing database connection..."))

(defmethod ig/halt-key! :plant-reader/handler [_ _]
  (println "Stopping handler..."))

;; ---- Integrant REPL Setup ----
(ig-repl/set-prep! (constantly system-config))

;; ---- Legacy API for backwards compatibility ----
(defn start-server 
  ([] (start-server 8080))
  ([port]
   (ig-repl/set-prep! (constantly (assoc-in system-config [:plant-reader/server :port] port)))
   (ig-repl/prep)
   (ig-repl/init)))

(defn stop-server []
  "Stop the server"
  (ig-repl/halt))

(defn restart-server []
  "Restart the server"
  (ig-repl/reset))

;; ---- Demo Function ----
(defn demo []
  "Run demo showing first 5 species and available categories"
  (println)
  (println "=== Plant Reader Demo ===")
  (println "First 5 Species:")
  (let [all-species (list-all-species)
        first-five (take 5 all-species)]
    (doseq [species first-five]
      (print-species species)))
  (println)
  (println (str "Categories: " (str/join ", " (get-categories))))
  (println)
  (println "To start the server, run: (start-server)")
  (println "To restart the server, run: (restart-server)")
  (println "To stop the server, run: (stop-server)")
  (println)
  (println "REPL workflow:")
  (println "  (ig-repl/prep)   - Prepare system")
  (println "  (ig-repl/init)   - Initialize system")
  (println "  (ig-repl/reset)  - Reset system")
  (println "  (ig-repl/halt)   - Halt system")
  (println)
  (println "Then visit: http://localhost:8080")
  (println "API docs at: http://localhost:8080/api-docs"))

;; ---- Main Function for Standalone Running ----
(defn -main [& args]
  "Main entry point"
  (let [port (if (first args) (Integer/parseInt (first args)) 8080)]
    (start-server port)))

;;; Example usage:

(comment

 (demo)
 (start-server)        ; Start server on port 8080
 (start-server 3000)   ; Start server on port 3000
 (stop-server)         ; Stop the server
 (restart-server)      ; Restart the server
 
;; REPL development workflow:
 (ig-repl/prep)        ; Prepare the system
 (ig-repl/init)        ; Initialize and start
 (ig-repl/reset)       ; Reset the system
 (ig-repl/halt)        ; Stop the system
  )
;;; 
;;; Then visit http://localhost:8080 in your browser!
;;; API documentation: http://localhost:8080/api-docs

;;; For development, start REPL with: clj -M:repl
;;; For production, run with: clj -M -m plant-reader.core

