(ns brianwitte.prod
  (:require [brianwitte.core :as app]))

(defn main []
  ;; Make production adjustments here
  (app/bootup (js/document.getElementById "app")))
