(ns brianwitte.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [brianwitte.ui :as ui]
            [brianwitte.data :as data]
            [replicant.dom :as r]
            [cljs.core.async :refer [<!]]))

(defonce app-state (atom {:species []
                          :categories []
                          :loading? true
                          :error nil}))

(defn load-initial-data []
  "Load species and categories from API"
  (go
    (swap! app-state assoc :loading? true :error nil)
    
    ;; Fetch species
    (let [species-response (<! (data/fetch-species))]
      (if (:error species-response)
        (swap! app-state assoc :error "Failed to load species" :loading? false)
        (swap! app-state assoc :species (:species species-response))))
    
    ;; Fetch categories
    (let [categories-response (<! (data/fetch-categories))]
      (if (:error categories-response)
        (swap! app-state assoc :error "Failed to load categories" :loading? false)
        (swap! app-state assoc :categories (:categories categories-response))))
    
    (swap! app-state assoc :loading? false)))

(defn main [el]
  (r/render el (ui/render-page @app-state)))

(defn bootup [el]
  ;; Load data on startup
  (load-initial-data)
  
  ;; Set up a watcher to re-render on state changes
  (add-watch app-state :render
             (fn [_ _ _ new-state]
               (r/render el (ui/render-page new-state))))
  
  ;; Initial render
  (main el))
