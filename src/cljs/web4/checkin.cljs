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
(defonce checkin-data (atom {:vid 0 :add-id [] }))

; ---- check in
(defn checkin-visit! [doc]
 ;doc like:
 ;{:vstatus sched, :vscore 5, :add-ids [{:etype LunaID, :id 11469}], :visitno 1, :fname Bart, :age 6.05886379192334, :sex M, :vid 3893, :ids [{:id nil, :etype nil}], :hand R, :studys [{:study MEGEmo, :cohort Control}], :lname Simpson, :note this is not a real visit, :tasks [PVLQuestionnaire fMRIRestingState SpatialWorkingMem ScanSpit], :dob 2010-01-01T05:00:00Z, :pid 1182, :add-task , :notes [TEST Test CogR01 Testing ringreward scan 1], :vtype Scan, :googleuri nil, :vtimestamp 2016-01-23T18:00:00Z}
 (let 
   [url (str "/visit/"(:vid doc)"/checkin" )
   pid  (:pid doc) ]
  (h/post-json url doc (fn[r] 
    (js/console.log "submited checkin " (str doc) r )
    (h/gotohash (str "/search?selected-pid=" pid ))
    ; TODO: we go there but we don't update from before checkin
    (v/set-person-visits! pid )
   ) )
))
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

(def checkin-form 
 [:div.checkin.form
   [:div.row
     [:div.col-xs-2
       [:div.input-group
         [:input.form-control {:field :text :id :vscore :placeholder "4.5"}] 
         [:div.input-group-addon "/5"]]
     ]

  ;   ; Task
  ;   [:div.col-xs-3 
  ; 	    [:div {:field :typeahead 
  ;                       :input-class "form-control"
  ;                       :data-source h/task-source
  ;                       :list-class "typeahead-list"
  ;                       :item-class "typeahead-item"
  ;                       :highlight-class "highlighted"
  ;                       :id :task-add :input-placeholder "TASK"}] ]

  ; ]
  ; [:div.row
   ; Notes
     [:div.col-xs-8 [:textarea.form-control { :field :textarea :id :note :placeholder "NOTES"} ] ]
   ]
 ]
)


(defn drop-task [doc rm]
 (let [ newlist (filter #(not(= % rm)) (:have @doc) ) ]
  (swap! doc assoc :have newlist )
 )
)
(defn add-taskdoc! [doc t]
 (let [ newlist (filter #(not(= % t)) (:have @doc) ) ]
  (swap! doc assoc :have (conj newlist t) )
 )
)
;(defn  add-taskdoc! [doc task]
; "add task t to atom doc"
; (js/console.log "add task:" task (str @doc) )
; (when (not (some #{task} (:tasks @doc)))
;  (swap! doc assoc :task (conj (:add-task task) (:tasks @doc)))
; )
;)
(defn taskclass [t tasks exp]
 (str
   (if #(some #{t} tasks ) "addedtask" )  " "
   (if #(some #{t} exp   ) "should-havetask"))
)
; TODO fix this mess
;      tasks is an atom list, part of a map before
(defn task-comp [tasks tlist classes]
      [:div.tasklist 
        ;(name _key) 
        (for [t tlist ]
         ^{:key t}
          [:div.task 
            ; if we have 'addedtask' then we want to drop on clik
            ; otherwise we want to add
            {:on-click #(if (re-matches #".*addedtask.*" classes) 
                    (drop-task tasks t)
                    (add-taskdoc! tasks t)  )
             :class classes } 
           t ] )
      ]
)

(defn find-missingtask [master newtasks]
 "find all elements of 'newtask' not inside 'master'"
 (filter #(not(some #{%} master)) newtasks)
)
(defn notsame [a b]
  (not (and (empty? (find-missingtask a b)) 
       (empty? (find-missingtask b a)) ))
)
(defn recalc-tasks! [tasks]
 ;(js/console.log "recalcing tasks\n\t" (str @tasks))
 (let [
    have (:have @tasks)
    exp  (:exp @tasks)
    missing (find-missingtask have exp)
    extra   (find-missingtask exp  have)
    overlap (h/dups (concat have exp))
    ; both need to be diff so we dont get stuck in a loop
    needrecalc (or (notsame missing (:missing @tasks))
                   (notsame extra   (:extra   @tasks))
                   (notsame overlap (:overlap @tasks)))
 ]

 (when needrecalc
  (js/console.log "swaping tasks!")
  (swap! tasks assoc :missing missing)
  (swap! tasks assoc :overlap overlap)
  (swap! tasks assoc :extra   extra)
  ;(swap! checkin-data assoc :tasks have)
)))


(defn enter-add-task [e tasks tasklist]
  (js/console.log (.-charCode e) (str tasklist ))
  (when (and (= (.-charCode e) 13)  (not(nil?(first tasklist))))
   (js/console.log "enter add" (str (first tasklist)))
   (add-taskdoc! tasks (first tasklist)))
)
(defn add-task-comp [tasks]
  (let [doc (atom [])]
  (fn[]
   [:div [:input.form-control {
           :type "text" 
           :id "search-tasks" 
           :placeholder "ADD TASKS"
           :on-change #(reset! doc (h/task-source (-> % .-target .-value)) )
           :on-key-press #(enter-add-task % tasks @doc)
        } ]
    ;[:div (for [t @doc] ^{:key (str "add-"t)}[:div t]) ]
    [task-comp tasks @doc "should-havetask" ]
    ;[:div (str @doc) ]
])))

(defn update-task-checkin-data [k a o n] 
(let [havelist (:have n)]
  (js/console.log (str "trigger " k " updating checkin-data to " havelist))
  (swap! checkin-data assoc :tasks havelist ) 
))

(defn checkin-visit-form-comp []
 (let [
    tasksDS (current-tasks) 
    ;tasksDS [{:task "fMRIRestingState"} {:task "spatialWorkingMem"} {:task "ScanSpit"}]
    expected-tasks (doall (map :task tasksDS) )

    tasks (atom {:exp expected-tasks 
                 :have expected-tasks 
                 :overlap expected-tasks
                 :missing [] :extra []} )

    doc (atom {
          :add-task "" 
	     	})
   ]
   (fn []
   (recalc-tasks! tasks)
   (add-watch tasks :addtaskwatcher #(fn[k a o n] (recalc-tasks! a) ) )
   (add-watch tasks :swaphave update-task-checkin-data )
    [:div 

      [:div.row 
       [:div.col-xs-3 [:h4 " Tasks: " ]]
       [:div.col-xs-8  
         (task-comp tasks (:overlap @tasks)  "addedtask should-havetask")
         (task-comp tasks (:extra   @tasks)  "addedtask")
       ]
      ]
      [bind-fields checkin-form doc ] 
      [:div.col-xs-4 [:div.btn.btn-default {:on-click #(checkin-visit! (merge @checkin-data @doc)) } "Checkin"  ]]
      [:h2.checkinhead "Additional Tasks"]
      [add-task-comp tasks]
      (task-comp tasks (:missing @tasks)  "should-havetask")
      ;[:div (str @tasks)]
    ]
   )
))

(defn add-id-doc! [doc e]
 (let [etype (-> e .-target .-value) 
      newidmap [{:etype etype :id "0"}]
      newlist  (concat newidmap (:add-ids @doc)) ]
  (js/console.log "addid! "  etype (str newidmap))
  (swap! doc assoc :add-ids newlist  )
  ;(set! e "")
))

(defn changeid [doc e]
 (let 
  [selectval (-> e .-target .-value)]
   (if (= "" selectval)
    (swap! doc assoc :etype selectval ) 
    ;else
    (h/get-json (str "/newest/enroll/" selectval)
     (fn[r]
       (swap! doc assoc :etype selectval ) 
       (swap! doc assoc :id (-> r :data first :id) ) 
       (js/console.log selectval " to " (str @doc))
    )))
))

(defn remove-newid! [id]
 ;(let [newlist (h/drop-nth i (:add-ids @checkin-data))]
 (let [newlist 
         (filter 
             #(not (and 
                  (= (:id id) (:id %) ) 
                  (= (:etype id) (:etype %)))) 
          (:add-ids @checkin-data))]
  (js/console.log "dropping id " id " in list" (str (:add-ids @checkin-data)))
  (swap! checkin-data assoc :add-ids newlist)
 )
)


(defn id-comp [add-ids]
   (let 
       [doc (atom {:etype "" :id ""})] 
 (fn[]
 [:div
  [:h4 "IDs" ]
  [:div.row
   ;[:select.form-control {:on-change #(add-id-doc! add-ids %) :value ""}
    [:div.col-xs-2 [:select.form-control
       {:value (:etype @doc)
        :on-change #(changeid doc %) } 

     [:option ""]  
     (for [e (:etypes @h/autocomplete-lists)] 
         ^{:key (str e)}[:option (str (:etype e))  ] ) 


    ]]

  [:div.col-xs-3 
     [:input.form-control 
       {:type "text" 
        :value (:id @doc) 
        :on-change (fn[e] (swap! doc assoc :id (-> e .-target .-value)))
        :visibility #(not(= "" (:etype @doc))) } ]
 ] 
 [:div.col-xs-3 
     [:input.form-control {:type "submit"  :value "add"
        :on-click #(swap! checkin-data assoc :add-ids (conj (:add-ids @checkin-data) @doc))} ]
 ]
 ;[:div (str @doc)  ]
 ;[:div (-> @h/autocomplete-lists :etypes str)  ]
 ]

 ;; LIST IDS
 ; newly added ids
 [:ul.checkin.ids
   (map-indexed (fn [i id]
     ^{:key (str "addid " id i)}[:li
         {:on-click #(remove-newid! id) }
         (str (:etype id)  ": "  (:id id) ) ]

         ;[:input :type "text" :name (str i (:etype id)) :value (:id id)  ]]
    ) (:add-ids @checkin-data) )
 ]

 ; known ids
 [:ul.checkin.ids  (map (fn[x] (when (not(nil? (:id x))) ^{:key (vals x)}[:li (:etype x) ": " (:id x)] ) (:add-ids @checkin-data) )) ]
]
)))

(defn checkin-page []
 [:form
   ; subject info
   [:h1 (:fname @checkin-data) " " 
        (:lname @checkin-data) " " 
        (:vtype @checkin-data) 
        (-> @checkin-data :age (h/roundstr 1) str) "yo "
        (:sex @checkin-data) " "
        (:vtimestamp @checkin-data) 
   ]

   [:div.info
        [:span.id "pid " (:pid @checkin-data)] 
        [:span.id "vid " (:vid @checkin-data) ]
   ]

   ;; add new id
   [id-comp checkin-data]

   ; when it's sched. then we can checkin
   (when (= (:vstatus @checkin-data) "sched" )
     [checkin-visit-form-comp]
   )




   ;;;;;;
   ; studies
   [:h2 "Studies" ]
   [:ul.checkin.studys  (map (fn[x] ^{:key (vals x)}[:li (:study x) " - " (:cohort x)] ) (:studys @checkin-data) ) ]
   ; notes
   [:h2 "Previous Notes" ]
   (map-indexed (fn[i x] ^{:key (str i "-note")}[:div.checkin.note x] ) (:notes @checkin-data) )


   [:div (str @checkin-data) ]
  
 ]
)
