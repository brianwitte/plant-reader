(ns brianwitte.ui)

(defn render-species-item [species]
  [:li {:key (:id species)}
   [:h3 (:common-name species)]
   [:p {:style {:font-style "italic"}} (:scientific-name species)]
   (when (:category species)
     [:p "Category: " (:category species)])
   (when (:height species)
     [:p "Height: " (:height species)])])

(defn render-page [{:keys [species categories loading? error]}]
  [:div {:style {:padding "20px"}}
   [:h1 "Plant Reader"]
   
   (cond
     loading? [:p "Loading species data..."]
     error [:p {:style {:color "red"}} error]
     :else
     [:div
      [:h2 (str "Found " (count species) " species")]
      
      (when (seq categories)
        [:div
         [:h3 "Categories:"]
         [:ul
          (for [category categories]
            [:li {:key category} category])]])
      
      [:h3 "Species:"]
      [:ul
       (for [s species]
         (render-species-item s))]])])
