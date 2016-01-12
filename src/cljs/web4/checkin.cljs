(ns web4.checkin
    (:require [reagent.core :as reagent :refer [atom]]

              ;get data
              [ajax.core :refer [GET POST] ]

              [secretary.core :as secretary :include-macros true]

              [reagent-forms.core :refer [bind-fields init-field value-of]  ]

              ;debug show data
              [json-html.core :refer [edn->hiccup]]

              ; helping functions -- can't do this or we recusive forver
              [ web4.helpers :as h ]
              ;[ web4.visits  :as v ] -- visit requires this, not the other way
              
    ))

; -- single visit (checkin)
(defonce checkin-data (atom {:vid 0}))

; ---- check in
(defn checkin-visit! [doc]
 (js/console.log doc)
)
; fitlter tasks to those in study and vtype
(defn set-current-tasks [study vtype studylist]
     ;(js/console.log study vtype )
     (filter #(and(= (:study %) study) 
                    (some? (some #{vtype} (:modes %)) ) )   studylist)
)
; set tasks we should have by given the current visit state
(defn current-tasks []
  ;(doseq [x (:studys @checkin-data)] (set-current-tasks (:study x) (:vtype @checkin-data)  (:tasks @autocomplete-lists) ) )
  (let [tasks    (:tasks @h/autocomplete-lists)
        studys   (map :study (:studys @checkin-data))
        vtype    (:vtype @checkin-data)
       ]
  (flatten (doall (map #(set-current-tasks % vtype  tasks) studys)  ))
  ;(js/console.log (str curtasks))
  ;(flatten curtasks)

))

(defn checkin-add-task [x]
  (js/console.log "checkin-add-task hit" x)
)
(def checkin-form 
 [:div.checkin.form
   [:div.row
     [:div.col-xs-1
       [:div.input-group
         [:input.form-control {:field :text :id :vscore :placeholder "4.5"}] 
         [:div.input-group-addon "/5"]]
     ]

     ; Task
     [:div.col-xs-3 
		    [:div {:field :typeahead 
                         :input-class "form-control"
                         :data-source h/task-source
                         :list-class "typeahead-list"
                         :item-class "typeahead-item"
                         :highlight-class "highlighted"
                         :id :task-add :input-placeholder "TASK"}] ]

   ]
   ; Notes
   [:div.row
     [:div.col-md-4 [:textarea.form-control { :field :textarea :id :note :placeholder "NOTES"} ] ]
   ]
 ]
)
(defn checkin-visit-form-comp []
 (let [
    expected-tasks (current-tasks)
    tasks-only     (doall (map :task expected-tasks ) )
    doc (atom {
		    :should-havetasks tasks-only
          :add-task "" 
          :tasks tasks-only
	     	})
   ]
   (fn []
    [:div 
      [bind-fields checkin-form doc ] 
       ; (fn[k v {:keys [add-task tasks] :as doc}]
       ;   (when (some? add-task )
       ;     ;(js/console.log (str add-task ) (str tasks))
       ;     (assoc-in doc [:tasks] (conj tasks add-task))
       ;   ))]
      [:div "tasks: " (str (:tasks @doc) )]
      ;[:div.row {:style {:background "blue"}}
      ;   (doseq [x (:tasks doc)] [:div.task x] )
      ;]
      ;[:br {:style {:clear "both"}} ]
      [:div.col-xs-4 [:div.btn.btn-default {:on-click #(checkin-visit! @doc) } "Checkin"  ]]
    ]
   )
))
(defn checkin-page []
 [:div
   [:h1 (:vtype @checkin-data) " Checkin " [:span.id (:vid @checkin-data)] ]

   ; subject info
   [:h2 (:fname @checkin-data) " " 
        (:lname @checkin-data) " " 
        (-> @checkin-data :age (h/roundstr 1) str) "yo "
        (:sex @checkin-data)
        [:span.id (:pid @checkin-data)]  ]

   ; studies
   [:ul.checkin.studys  (map (fn[x] ^{:key (vals x)}[:li (:study x) " - " (:cohort x)] ) (:studys @checkin-data) ) ]
   ; ids
   [:ul.checkin.ids  (map (fn[x] (when (not(nil? (:id x))) ^{:key (vals x)}[:li (:etype x) ": " (:id x)] ) (:ids @checkin-data) )) ]

   [:div "visit on " (:vtimestamp @checkin-data) ]
   ; when it's sched. then we can checkin
   (when (= (:vstatus @checkin-data) "sched" )
     [(checkin-visit-form-comp)]
   )

   ; notes
   (map-indexed (fn[i x] ^{:key (str i "-note")}[:div.checkin.note x] ) (:notes @checkin-data) )

  
   [:div (str @checkin-data) ]
 ]
)
