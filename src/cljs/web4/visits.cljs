(ns web4.visits
    (:require [reagent.core :as reagent :refer [atom]]

              ;get data
              [ajax.core :refer [GET POST] ]

              [secretary.core :as secretary :include-macros true]

              [reagent-forms.core :refer [bind-fields init-field value-of]  ]

              ; use data
              [cljs-time.core :as tc ]
              [cljs-time.format :as tf ]

              ;debug show data
              [json-html.core :refer [edn->hiccup]]

              ; helping functions -- can't do this or we recusive forver
              [ web4.helpers :as h ]
              [ web4.messages :as m ]
; cannot include here, becomes circular
;              [ web4.checkin :as checkin ]
              
    ))



; contains all the visits
(defonce person-state (atom {:pid 0 :visits [] :info nil}))
; do we have a pesron in the global state
(defn have-person []
  (and (:pid @person-state) (> (:pid @person-state) 0) )
)

;
(defn visit-options-source [opttyp text]
 ;(js/console.log text (str  (opttyp @autocomplete-lists)))
 (filter #(-> % (.toLowerCase) (.indexOf text) (> -1)) (opttyp @h/autocomplete-lists))
)

; contains how to display the visits
(defonce visit-state-display (atom {:collapsed false}))

(defn set-person-visits! [pid]
  (js/console.log  "setting person info for " pid)
   (GET (str "/person/" pid "/visits" ) 
       :keywords? true 
       :response-format :json 
       :handler (fn [response] 
            ;(js/console.log "response:" (type response) (str response) )
            (swap! person-state assoc :pid pid)
            (swap! person-state assoc :visits response)

            ; get persons info
            (GET (ajax.core/uri-with-params "/people" 
                   {:pid pid :study "" :eid "" :hand "" 
                    :fullname "" :sex "" :mincount 0 :minage 0 :maxage 200 :offset 0}) 
                 :keywords? true 
                 :response-format :json 
                 :handler (fn [response] 
                      (js/console.log "setting visit person response:" (type response) (str response) )
                      (swap! person-state assoc :info (first (:data response )))
                 )
             )
       )
   )

)


; needs checkin to be included, but we need visit to be included in checkin
;(defn set-visit! [vid]
; (h/get-json (str "/visit/" vid) 
;    (fn [response]
;     (reset! checkin/checkin-data (first (:data response)))
;     (js/console.log "set-visit! setting checkin-data -- resonse:" (first (:data response) ))
;)))

;--- note
(defn render-note [ni]
 ^{:key (:nid ni)} 
 [:div 
   [:div  (:note ni) " - " (:ra ni) " " [:span {:class "id"} (:nid ni) ]   ]
 ]
)



; show tasks tasks -- link if has data
(defn visit-task-idv-comp [t]
 
 (def attr (if (:hasdata t) 
   (hash-map :on-click #(h/gotohash (str "/task/" (:vtid t) )  ) 
             :class "visittask link"
   )
   (hash-map :class "visittask" )
 ))

 ^{:key (:vtid t)} [:div attr (:task t) ]
)

(def idv-vid-form 
  [:div.row.idv-vid-form
    [:div.col-xs-8 [:input.form-control {:field :text :id :note :placeholder "NOTES"}] ]
    [:div.col-xs-4 [:div {:field :datepicker :id :visitday :date-format "yyyy/mm/dd" :inline true}]]
  ]
)

; do /vist/id/{noshow,cancel,resched}
(defn update-visit [how doc]
  (POST (str "/visit/" (:vid doc) "/" how  )
       :keywords? true
       :format :json
       :response-format :json 
       :params doc
       :handler (fn [response] 
            ; print 
            (js/console.log how " response:" (type response) (str response) )

            (m/add-error-state! response)
            ; update visit list again
            (set-person-visits! (:pid @person-state))
       )
  )
)




(defn update-visit-has-note [how doc]
 (if (and (not(= how "cancel") ) (clojure.string/blank? (:note doc)))
   (m/add-error-state! {:warning (str "can not " how " need a note!")})
   (update-visit how doc)
 )
)



(defn add-visit! [doc]
 ;; make timestamp
 (def tvector (flatten [ 
   (vals (select-keys (doc :visitday ) [:year :month :day ] )) 
   (clojure.string/split (:time doc) ":" )
  ]))
 (def timestamp (apply tc/date-time tvector))

 (def senddata (merge doc  {:vtimestamp (str timestamp)}) )
 (js/console.log "time:"  timestamp "\nsenddat: " (str senddata) "\ndoc:" (str doc))

 (when (have-person)
  (POST (str "/person/" (:pid @person-state) "/visit"  )
       :keywords? true
       :format :json
       :response-format :json 
       :params senddata
       :handler (fn [response] 
            ; print 
            (js/console.log "response:" (type response) (str response) )
            (m/add-error-state! response)

            ; TODO
            ; check response, append to error messagse

            ; update list again
            (set-person-visits! (:pid @person-state))
       )
  )
 )
)
; visit-form via reagent-forms
(def visit-form-date 
  [:div

   ; date, time , duration
   [:div.row
    [:div.col-xs-4 [:div {:field :datepicker :id :visitday :date-format "yyyy/mm/dd" :inline true}]]
    [:div.col-xs-4 
       [:div.input-group
         [:input.form-control {:field :text :id :time}]
         [:div.input-group-addon "/24:00"]
      ]
    ]
    [:div.col-xs-4 [:div.input-group
      [:input.form-control {:field :text :id :dur}] 
      [:div.input-group-addon "hours"]
    ]]
   ]

   ; study and cohort
   [:div.row
     [:div.col-xs-4 [:div {:field :typeahead 
                             :input-class "form-control"
                             :data-source #(visit-options-source :studies %)
                             :list-class "typeahead-list"
                             :item-class "typeahead-item"
                             :highlight-class "highlighted"
                             :id :study :input-placeholder "STUDY"}] ]
     [:div.col-xs-4 [:div {:field :typeahead 
                             :input-class "form-control"
                             :data-source #(visit-options-source :cohorts %)
                             :list-class "typeahead-list"
                             :item-class "typeahead-item"
                             :highlight-class "highlighted"
                             :id :cohort :input-placeholder "cohort"}] ]
   ]

   ; type and number
   [:div.row
     [:div.col-xs-4 [:div {:field :typeahead 
                             :input-class "form-control"
                             :data-source #(visit-options-source :vtypes %)
                             :list-class "typeahead-list"
                             :item-class "typeahead-item"
                             :highlight-class "highlighted"
                             :id :vtype :input-placeholder "VISIT TYPE"}] ]
     [:div.col-xs-4 [:input.form-control {:field :text :id :visitno :placeholder "VNUM"}] ]
   ]

   ;Notes
   [:div.row
     [:div.col-md-8 [:textarea.form-control { :field :textarea :id :note :placeholder "NOTES"} ] ]
   ]
  ]

   ;[:select {:field :list :id :dur} (map (fn[k] [:option {:key k} k]) [.5 1 1.5 2] )]
   ;[:input.form-control {:field :datepicker :id :visitday :date-format "yyyy-mm-dd" :inline true}]
)


(defn new-visit-form []
 (let [
    showadd (atom false)
    doc (atom {:visitday {:year 2016 :day 01 :month 01}  
                :time "13:00"
                :visitno 1
                :dur 1 
                :ra "testRA"
                :cohort "Control"
                :study "" 
                :vtype ""
                :note ""} )]
  (fn []
   [:div.visit-form
    [:div.toggle [:a {:on-click #(do (js/console.log @showadd) (swap! showadd not)) } "add visit" ] ]
    (when  (and have-person @showadd)
     [:div
       [bind-fields visit-form-date doc]
       [:div.btn.btn-default {:on-click #(add-visit! @doc) } "Add Visit" ]
       ;[:div (edn->hiccup @doc)]
       
     ]
   )]
  )

))


(defn visit-idv-actions [visit]
  ; TODO: use current visitdate as date
  (let [doc (atom {:note "" :date {} :vid (visit :vid) :ra "testRA" })] 
  (fn[]
      [:div.visitactions
       [bind-fields idv-vid-form doc]
       [:a {:on-click #(h/gotohash (str "#/visit/" (visit :vid) "/checkin" )) } "checkin" ]"|"
       [:a {:on-click #(update-visit-has-note "noshow" @doc) } "noshow"  ]"|"
       [:a {:on-click #(update-visit-has-note "cancel" @doc) } "cancel"  ]"|"
       [:a {:on-click #(update-visit-has-note "resched" @doc) } "resched" ]
      ]
  )
  )
)


(defn visit-idv-comp [visit]
   
   (def scorecolor (h/colorspctm (visit :vscore)))
   ;(js/console.log "score: " scorecolor (visit :vscore))
   ^{:key (:vid visit)}[:li {:id (visit :vid) :class (str (visit :vstatus) " visititem " (visit :vtype) ) }

    ; DATE and AGE
    [:div {:class "visitdate"} 
       (str (h/notime-datestr (visit :vtimestamp)) " - " (h/roundstr (visit :age) 2) )
       [:div {:class "id"} (visit :vid) ] ]
    ; ACTIONS
    (when (= (visit :vstatus) "sched" )
      [ (visit-idv-actions visit) ] 
    )
    ; INFO (type/study)
    [:div {:class "visitinfo"  :style { :borderColor scorecolor}  } 
        (visit :vtype)" - " (visit :study)  " - "  (visit :vscore) ]
      ; TASKS
      [:div {:class "visittasks" }  
          (map visit-task-idv-comp (visit :tasks)) ]
      ; NOTES
      [:div {:class "visitnotes"}
         (map #(render-note % ) (visit :notes) )
      ]

      ; STRUCT
      ;[:div (str visit) ]
    ;(when (not (:collapsed @person-state-display))
    ;)
   ]
)

;; -------
;; Person/Visit

; person info
(defn person-info-comp []
 (def info (:info @person-state))
 (def dropinfo (get-in @person-state [:info :maxdrop]))

 [:div {:class "person-info"}
  ;[:div "PERSONSTATE:" (str @person-state) ]
  [:h2 (clojure.string/join " " (:ids info)) [:span.id (:pid @person-state) ] ]
  [:h3 (str (:fname info) " " (:lname info)) ] 
  (when (not (nil? dropinfo)) [:div "Drop: " (str dropinfo) ])
 ;[:div
 ;  (edn->hiccup (:info @person-state))
 ;]

])

; person component: person, visits, contacts
(defn person-comp []
 [:div {:class "person"} 

   [person-info-comp ]

   (when (have-person)
     [:div {:class "visit-add col-md-5"}
        [ (new-visit-form )  ]
     ]
   )

   [:div {:class "visit-cntnr"} 
   (map visit-idv-comp (:visits @person-state)) ]
 ]
)
