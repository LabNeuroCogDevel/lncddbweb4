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
            (GET (str "/people?pid=" pid)
                 :keywords? true 
                 :response-format :json 
                 :handler (fn [response] 
                      (js/console.log "setting visit person response:"
                                       (type response) (str response) )
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

    [:div.visit-form-toggle 
      [:button.form-control.btn-info
        {:on-click #(do (js/console.log @showadd) (swap! showadd not)) } 
          "toggle add visit"
    ] ]

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
 (let [
    ; border to indicate score
    scorecolor (if (:vscore visit) (h/colorspctm (visit :vscore)) "#fff") 
    drops (->> visit 
           :notes 
           ; false if any droplevel has text
           (map #(-> % :droplevel nil?))
           distinct
           sort  ; (false true) 
           first   
           (= false) ; if there is a false, we dropped
         )

    dropclass  (if drops "drop-visit" "")
   ]
   ;(js/console.log "score: " scorecolor (visit :vscore))
   ^{:key (:vid visit)}
   [:li
      {:id (visit :vid) 
       :class (str (visit :vstatus) " visititem " (visit :vtype) " " dropclass ) 
      }

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
))

;; -------
;; Person/Visit

; parse IDS
(defn emp-luna [ids]
 "lazy/hack/cludge to emphasize luna ids"
 (for [id (sort #(< (count %1) (count %2)) ids) ]
  (let [isluna (if (= 5 (count id)) "luna" "notluna")]
  ^{:key (str "id" id)}[:span.idtype {:class isluna} (str id " ")]
 ))
)

; person info
(defn person-info-comp []
 (let [
    info (:info @person-state)
    maxdrop (get-in @person-state [:info :maxdrop])
    pid  (:pid @person-state) 
 ]

 [:div {:class "person-info"}
  ;[:div "PERSONSTATE:" (str @person-state) ]

  (when (not (nil? maxdrop)) ^{:key "maxdrop"}[:h2.maxdrop "MaxDrop: " (str maxdrop) ])
  ; ID and drop
  [:h2.ids 
     (emp-luna (:ids info)) 
     (when (> pid 0) [:span.id pid])
  ]

  ; NAME
  [:h3.name (str (:fname info) " " (:lname info)) ] 
  [:br]

  ;[:div (edn->hiccup (:info @person-state)) ]

]))

(defn listselect [all k seldoc]
 [:ul.list-selection 
 
 [:li [:a 
    { :class "btn-sm btn-default toggleall"
     :on-click (if (empty? (k @seldoc))
      #(swap! seldoc assoc k all)
      #(swap! seldoc assoc k []))} "x"] ]

 (doall (for
  [a all]
  (let [exists (some #{a} (k @seldoc))]
   ^{:key (str "sel" (name k) a)}
   [:li [:a.btn 
     {:class (str "btn-sm " 
            (if exists "btn-primary" "btn-default" )
            " " a )
      :on-click #(if exists 
           (swap! seldoc assoc k (filter (fn[x] (not(= x a))) (k @seldoc)) )
           (swap! seldoc assoc k (conj (k @seldoc) a) )
           )
     } 
     a
   ]])
  ))
 ]
)

(defn visits-comp []
 (let [
  visits (:visits @person-state)
  ;allst  (->> visits (map :study) distinct sort)
  ;allvt  (->> visits (map :vtype) distinct sort)
  allst  (sort (distinct (map :study visits)))
  allvt  (sort (distinct (map :vtype visits)))
  sels   (atom {:studies allst :vtypes allvt })
 ] (fn []
  [:div {:class "visit-cntnr"} 
    
    ; ADD VISIT
    [:div {:class "visit-add "}
      (when (have-person) [new-visit-form])
    ]


    ; NARROW 
    [:div.row
      [:div.inline [listselect allst :studies sels] ]
      [:div.inline [listselect allvt :vtypes  sels] ]
    ]
   
    ; ALL VISITS
    (doall (map visit-idv-comp (filter 
      #(and (some #{(:vtype %)} (:vtypes @sels))
            (some #{(:study %)} (:studies @sels)))
      (:visits @person-state) )))
  ]
)))

; -------------------- CONTACTS
(defonce contact-edit-state (atom {:edit false}))
(defn toggle-contact-comp  []
 [:a.btn.glyphicon.glyphicon-pencil
    {:class (if (:edit @contact-edit-state) "btn-primary" "btn-default")
     :on-click #(swap! contact-edit-state assoc :edit (not(:edit @contact-edit-state))) }
 ]
)

(defonce person-contact (atom {:contacts []}))

; ---- actions
(defn update-contacts []
  (h/get-json 
    (str "person/" (str (:pid @person-state)) "/contacts") 
    (fn[r] 
      (js/console.log (str r))
      (swap! person-contact assoc :contacts (:data r)) )  
))

(defn contact-note [pid cid note]
 (h/post-json (str "contact/" cid "/note")
         {:note note :pid pid}
         (fn[r] (js/console.log "note" (str r)) (update-contacts)))
)
(defn contact-ban [pid cid note]
 (h/post-json (str "contact/" cid "/ban")
         {:note note :pid pid}
         (fn[r] (js/console.log "ban" (str r)) (update-contacts)))
)

(defn add-contact [sendaway]
  (js/console.log "adding as contact: " (str sendaway))
   (h/post-json (str "person/" (:pid @person-state) "/addcontact")
                sendaway
                (fn[r] (js/console.log "response from add contact:" (str r))
                       (update-contacts)))
)


;----- forms
; notes for contact
(defn contact-note-form [pid cid]
 (let [notev (atom {:note ""}) ]
  (fn[]
  [:form
    [:div.row
      [:div.col-md-6 [:input.form-control
       {
        :value (:note @notev)
        :on-change #(swap! notev assoc :note (-> % .-target .-value) ) 
       }]]
      [:a.btn.btn-success 
        { :on-click #(contact-note pid cid (:note @notev))}
        [:span.glyphicon.glyphicon-earphone  " "]]
      [:a.btn.btn-danger  
         { :on-click #(contact-ban pid cid (:note @notev))}
         [:span.glyphicon.glyphicon-ban-circle " "]]
    ]
  ]
)))


; Contact - single contact info
(def contacts-contact-form
 [:div.contact-form
  [:div.col-md-4 [:input.form-control {:field :text :id :ctype :placeholder "type" }] ]
  [:div.col-md-4 [:input.form-control {:field :text :id :cvalue :placeholder "address/number"  }] ]
 ]
)

(defn add-contacts-contact [p]
 (let [ 
    p   (select-keys p [:who :pid :relation])
    doc (atom (merge {:ctype "" :cvalue ""} p))
  ](fn[]
  [:div.contact-contact
   [:div.row 
    [bind-fields  contacts-contact-form doc]
    [:button.btn.btn-sm-default.glyphicon.glyphicon-plus
      {:on-click #(add-contact @doc)} ]
   ] 
  ]
)))


; Contact - person
(def contacts-person-form
 [:div.row 
   [:div.col-md-4
    [:input.form-control {:field :text :id :who :placeholder "WHO"}]
   ]
   [:div.col-md-4
    [:input.form-control {:field :text :id :relation :placeholder "relation" }]
   ]]
)

(defn add-contacts-person [pid]
 (let [pdoc (atom {:pid pid :who "" :relation ""})]
  (fn[]
    [:div.contact-person
        [bind-fields contacts-person-form pdoc]
        [(add-contacts-contact @pdoc)]
    ]
)))


(defn contact-comp []
(let [
      info     ( :info  @person-state)
      dfltcont [{:pid (:pid @person-state)
                  :who (str (:fname info) " " (:lname info))
                  :relation "Subject"}] 
      contacts (:contacts @person-contact)
      contacts (if (empty? contacts) dfltcont contacts)]
  (fn[]
  (js/console.log "contacts: " (str contacts) (str dfltcont))
  [:div {:class "contacts"}
    [:div.contact-toggle [toggle-contact-comp]   ]
    ;[:div (str (:contacts @person-contact))]
    (doall (for [p contacts ]
      ^{:key (str "person-" (:who p))}
      [:div.contact-person
        [:h3 (:who p) [:span.id (:relation p)  ] ]
        (when (:lastcontact p) [:div.id [:b "last contacted "] (:lastcontact p)] )
        (for [c (:contacts p) ]
          ^{:key (str "contact-" (:cid c))}
          [:div.contact-contact
            [:b (:ctype c)] " " [:i (:cvalue c) " " [:span.id (:cid c)]  ]
             [:div.contact-note
               [(contact-note-form (:pid p) (:cid c))]
             ]
            (when (:notes c) (for [n (:notes c)]
             [:div.contact-note  
               [:i (:note n)] [:b (:ra n) ] " @" [:span.id (:ndate n)]
             ]))
          ]
        )
        
        (when (:edit @contact-edit-state) 
         [add-contacts-contact p]
        )
    ]
    ))
    [:br]

    (when (:edit @contact-edit-state) 
     [add-contacts-person (:pid @person-state)]
    )

  ]
)))

;; (defn notes-comp []
;;  (let [
;;     notes (atom ({:notes ""}))
;;     pid   (str (:pid @person-state))
;;   ] (fn[]
;; 
;;    [:div pid ]
;; ;  (js/console.log (str pid))
;; ;
;; ;  (h/get-json 
;; ;    (str "person/" pid "/notes") 
;; ;    (fn[r] (swap! notes assoc :notes r) )  )
;; ;
;; ;  [:ul 
;; ;   [:li (str @notes) ]
;; ;   ;(doall (for [n (:notes @notes)]
;; ;   ;  [:li (:note n) (:ndate n) - (:ra n) ]
;; ;
;; ;   ;))
;; ;  ]
;; )))

; ----------------------
; NOTES
(defonce person-notes (atom {:notes []}))
(defn update-notes []
  (h/get-json 
    (str "person/" (str (:pid @person-state)) "/notes") 
    (fn[r] 
      (js/console.log (str r))
      (swap! person-notes assoc :notes (:data r)) )  
))

(defn notes-comp []
  ; show notes
  [:ul 
   ;[:li (str @person-notes) ]
   (doall (for [n (:notes @person-notes)]
     ^{:key (str "note-" (:nid n))}
     [:li  (:note n) " @ " (:ndate n) [:br] " - " (:ra n)  ]
 
   ))
  ]
)


; watches to update contacts, notes (and visits?)
(add-watch person-state :updatenotes  
  (fn[w s p n] 
   (when (not(= (:pid n) (:pid p))) (do
     (update-notes)
     (update-contacts)
    ))))

; person component: person, visits, contacts
(defn person-comp []
 (let [
  ss (atom {:view "Visits"})
 ](fn[]
 [:div {:class "person"} 

   [person-info-comp ]
   
    [:ul {:class "nav nav-tabs"}
     (doall (for [n ["Visits" "Contacts" "Notes"]]
       ^{:key (str "nav" n)}
       [:li (assoc {:role "presentation" } :class (if (= n (:view @ss)) "active" ""))
           [:a {:on-click #(swap! ss assoc :view n) } n]
       ]
     ))
    ]
    (case (:view @ss)
     "Visits"    [(visits-comp)]
     "Contacts"  [(contact-comp)]
     "Notes"     [notes-comp  ]
     [:div "Unknown person tab"]
    )

 ]
)))
