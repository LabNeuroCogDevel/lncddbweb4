(ns web4.task
    (:require [reagent.core :as reagent :refer [atom]]

              ;helper and error state
              [web4.helpers  :as h]
              [web4.messages :as m]
             
))


; ----- tasks
(defonce visit-task-state (atom [{:vtid 0}]))

(defn set-visit-task! [vtid]
  (js/console.log  vtid)
  (h/get-json (str "/visit_task/" vtid) 
    (fn [response]
     (reset! visit-task-state (:data response))
     (js/console.log "visittask resonse: " (first (:data response) ))) )
)

(defn inputform [ob]
  ; (key ob) ; returns weird key + key_number
  (def k (name (key ob)) )
  [:tr 
    [:td [:label {:for k} k ] ]
    [:td [:input {:type "text" :value (val ob) :name k }  ] ]
  ]
)
(defn visit-task-task [t]
 [:div {:class "task"}
  [:h3 (:fname t) " " (:lname t)  " › " (:task t) " " [:span {:class "id"} "@ " (h/roundstr (:age t) 1) " yro" ]   ]
  [:table {:class "inputdiv"}
    (map inputform (:measures t) )
  ]
 ]
)
(defn visit-task-comp []
 [:div 
   (map visit-task-task @visit-task-state)
   ;(str @visit-task-state)
 ]
)

(defn visit-task-page []
   (visit-task-comp)
)
