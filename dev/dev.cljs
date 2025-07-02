(ns brianwitte.dev
  (:require [brianwitte.core :as app]))

(defonce el (js/document.getElementById "app"))
(defonce started (app/bootup el))

(defn ^:dev/after-load main []
  ;; Re-render after hot reload
  (app/main el))
