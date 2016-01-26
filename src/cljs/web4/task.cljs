(ns web4.task
    (:require [reagent.core :as reagent :refer [atom]]

              ;helper and error state
              [web4.helpers  :as h]
              [web4.messages :as m]
             
))


; ----- tasks
(defonce visit-task-state (atom [{:vtid 0}]))

(defn set-visit-task! [vtid]
  (js/console.log  "set visit task with vtid: " vtid)
  (h/get-json (str "/visit_task/" vtid) 
    (fn [response]
     (reset! visit-task-state (first (:data response)))
     (js/console.log "visittask resonse: " (first (:data response) ))) )
)

(defn inputval [e]
 (-> e .-target .-value)
)
(defn updatemeassure [k v]
  (let [nmes (assoc (:measures @visit-task-state) k v)]
   (swap! visit-task-state assoc :measures nmes)
))

(defn submit-task-update [doc]
 (let [
       vtid (:vtid doc)
       url (str "task/" vtid)
       mes (select-keys doc [:measures])
       pid (:pid doc)
  ]    
  (h/post-json url mes (fn[r] 
    (js/console.log "submited task for update" (str mes) r )
    (h/gotohash (str "/search?selected-pid=" pid ))
   ) )
))

(defn inputform [k]
  ; (key ob) ; returns weird key + key_number
  (let [ kname (name k) ]
  [:tr 
    [:td [:label {:for kname} kname ] ]
    [:td 
     [:input 
      {:name kname 
       :type "text" 
       :on-change #(updatemeassure k (inputval %))
       :value (k  (:measures @visit-task-state))   
      }  ] ]
  ]
))

(defn visit-task-task [doc]
  [:div {:class "updatetaskmeasures"}
    [:h3 
        (:fname doc) " " (:lname doc)  " › " 
        (:task doc) " " [:span {:class "id"} "@ " (h/roundstr (:age doc) 1) " yro" ]   ]

    [:table {:class "inputdiv"}
     [:tbody 
      (doall (for [ k (keys (:measures doc) )] 
         ^{:key (str k)}
           [inputform k]
     )) ]
    ]
 ]
)

(defn visit-task-comp []
 [:div 
   (visit-task-task @visit-task-state)
   [:div.col-xs-3 
    [:input.form-control 
       {:type "submit" :value "Submit!"
        :on-click #(submit-task-update  @visit-task-state)} ]]
   ;(str "state: " @visit-task-state)
 ]
)

(defn visit-task-page []
   (visit-task-comp)
)
