(ns web4.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]

              ;get data
              [ajax.core :refer [GET POST] ]
              ; use data
              [cljs-time.core :as tc ]
              [cljs-time.format :as tf ]
              [cljs.pprint :as pp ]
              ; pretty colors
              [inkspot.color-chart :as cc]
              ; date picker
              ;[cljs-pikaday :as pikaday]
              [reagent-forms.core :refer [bind-fields init-field value-of]  ]
              ;debug show data
              [json-html.core :refer [edn->hiccup]]
    )
    (:import goog.History))


;;; settings for postgrest haskell server
(def pgresturi "http://127.0.0.1:3001/")

;;; helpers
;(def colorspctm (cc/gradient :red :green 10) )
(defonce colorspctm (cc/color-mapper (cc/ui-gradient :miaka 10) 0 5))
; see also :drakula @ https://github.com/rm-hull/inkspot

(defn nilor [in defval]
  (if (nil? in) defval in)
)
(defn roundstr [fltstr n]
  (.toFixed (nilor (float fltstr) 0) n)
)

; dob is a string, make it a date
; and return a rounded string
(defn notime-datestr [dob]
   (tf/unparse (tf/formatters :year-month-day)  
               (if (nil? dob)
                 (tc/today-at 00 00)
                 (tf/parse (tf/formatters :date-time-no-ms) dob)
               )
          )  
)

; dispatch a url and set the address bar to that
; url is what comes after 'http.../#'
(defn gotohash [url]
 (aset (.-location js/window) "hash" url)
 (secretary/dispatch! url)
)

;; -------------------------
;; Models?

; --- error
(defonce error-state (atom {:error [] :warning [] :msg [] }))

; add to the message queue
(defn add-error-state! [r]
 (if (nil? r)
  ; if r is empty, that itself is an error
  (swap! error-state assoc :error (conj (@error-state :error) "no response for last action!")) 
  ; for each of the message types, add any existing to the message queue
  (doseq [k [:error :warning :msg]]
    (when (r k)
    (swap! error-state assoc k (conj (@error-state k) (r k))) )))
)
; remove from message queue
(defn rm-error [msgtype n]
 (def newlist (map-indexed (fn[i m] (when (not(= n i)) m))  (msgtype @error-state) ))
 (swap! error-state assoc msgtype newlist)
 ;(swap! error-state [msgtype n] nil )
)

; display a message
(defn idv-msg [msgtype msgs]
  (doall 
  (map-indexed (fn[n msg] 
     ^{:key (str (name msgtype) n)}
     [:div 
      {:class (str "msg " (name msgtype))
       :on-click #(rm-error msgtype n) }
      msg
     ]) 
   msgs ))
)

;display all messages
(defn msg-view-comp []
 [:div.msgs
    (doall (map #(idv-msg % (% @error-state) ) [:msg :error :warning] ))
 ]
)


; --- help that depends on error state
; get has the same call
; most places
(defn get-json [url handlefn]
   (GET url :keywords? true :response-format :json 
            :handler (fn[r]  (do (add-error-state! r) (handlefn r)) ))
     
)

;--- person+visit

; contains all the visits
(defonce person-state (atom {:pid 0 :visits []}))
; do we have a pesron in the global state
(defn have-person []
  (and (:pid @person-state) (> (:pid @person-state) 0) )
)

;
(defonce autocomplete-lists (atom {}))
(defn get-autocomplete-lists [opttyp]
   (GET (str "/study/" (name opttyp)) 
       :keywords? true :response-format :json 
       :handler #(swap! autocomplete-lists assoc opttyp %))
)
(defn get-autocomplete-lists! []
  (doseq [opttyp [:cohorts :studies :vtypes :tasks ] ] (get-autocomplete-lists opttyp))
  ;(js/console.log "updated add visit form options: " (str @autocomplete-lists))
)
(defn visit-options-source [opttyp text]
 ;(js/console.log text (str  (opttyp @autocomplete-lists)))
 (filter #(-> % (.toLowerCase) (.indexOf text) (> -1)) (opttyp @autocomplete-lists))
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
                      ;(js/console.log "response:" (type response) (str response) )
                      (swap! person-state assoc :info (get response 0))
                 )
             )
       )
   )

)

; -- single visit (checkin)
(defonce checkin-data (atom {:vid 0}))

(defn set-visit! [vid]
 (get-json (str "/visit/" vid) 
    (fn [response]
     (reset! checkin-data (first (:data response)))
     (js/console.log "resonse" (first (:data response) ))
)))

;--- note
(defn render-note [ni]
 ^{:key (:nid ni)} 
 [:div 
   [:div  (:note ni) " - " (:ra ni) " " [:span {:class "id"} (:nid ni) ]   ]
 ]
)

;--- people
; start out with a people search result
(defonce pep (atom [{:pid 0 :fname "Searching" :lname "For People" } ] ))
(defonce pep-search-state (atom {:study "" :eid "" :hand "" :fullname "" :sex "" :mincount 0 :minage 0 :maxage 200 :offset 0 :selected-pid 0}))
; (def pep-search-state (atom {:minage 0 :maxage 100 :fullname "Will" :sex "%" :hand "%" :mincount 0 } ))


; -- before we modified every vector
; -- now we are watching pep-search-state inside the view
;(defn set-select-on-pid [plist pid]
; (map (fn[p]
;   (if (= pid (:pid p) )
;       (assoc p :select true) 
;       (assoc p :select false) )
; ) plist)
;)

(defn select-person! [pid]
  ; setup visit stuff
  (set-person-visits! pid)
  ; add selected to this one
  ; remove selected from anyone else
  ; -- update state, updates css
  (swap! pep-search-state assoc :selected-pid pid)
  ; -- update each vector
  ;(swap! pep set-select-on-pid pid pep)
)

(defn render-person-row [si]
 ^{:key (:pid si)} 
  ;[:tr  { :on-click #(set-person-visits! (:pid si))}
  ; TODO WARNING 'when' on @pep-search-state causes warning
  [:tr  { :on-click #(select-person! (:pid si))
          :class (str "drop-" (:maxdrop si)
                  (when (= (:selected-pid @pep-search-state) (:pid si) )
                        " search-selected")) }

   [:td (doseq [id (:ids si)]  ^{:key (str si  id)}[:div {:class "search-id"} id ] )]
   [:td [:div (si :fname) " " (si :lname) ]
       [:div {:class "dob"} (notime-datestr (si :dob)) ]
   ]
   [:td {:class "monospaced"}
         (roundstr     (si :curage) 1 ) " " 
         (si :sex) " " 
         (si :hand) ]
   [:td (notime-datestr (:lastvisit si))]
   [:td (:numvisits si)]
   ;[:td (str si) ]
   ;[:td (first (si :ids)) ]
])

(defn get-pep-search! []
  ;(js/console.log (ajax.core/uri-with-params "/people" @pep-search-state)  )
  (GET (ajax.core/uri-with-params "/people" @pep-search-state) 
  ;(GET (str "/people?fullname=" (:fullname @pep-search-state )) 
  ;(GET (ajax.core/uri-with-params "/lists" pep-search-state) 
  ;(GET (str "/people/name?n=" (:fullname @pep-search-state )) 
       :keywords? true 
       :response-format :json 
       :handler (fn [response] 
            ;(js/console.log "response:" (type response) (str response) )
            (reset! pep response)
       )
  )
)

(def add-person-form 
 [:div.personaddform
   ; study and cohort
   [:div.row
     [:div.col-xs-4 [:input.form-control {:field :text :id :fname :placeholder "first"}] ]
     [:div.col-xs-4 [:input.form-control {:field :text :id :lname :placeholder "last" }] ]
     [:div.col-xs-4 [:div {:field :datepicker :id :dob :date-format "yyyy/mm/dd" :inline true}]]
   ]
   [:div.row
     [:div.col-xs-4 [:input.form-control {:field :text :id :sex :placeholder "M|F" }] ]
     [:div.col-xs-4 [:input.form-control {:field :text :id :hand :placeholder "R|L|U|A" }] ]
     [:div.col-xs-4 [:input.form-control {:field :text :id :source :placeholder "source" }] ]

   ]
 ]
)




(declare search-page)
; SEARCH ROUTE -- here b/c search-path needs to be defined before update-pep-search!
(secretary/defroute search-path "/search" [query-params]
  ;(js/console.log (pr-str  query-params) query-params)
  (doseq [pk (keys @pep-search-state)] 
    (let [v (pk query-params)]
    (when (not(nil? v))
     (swap! pep-search-state assoc pk v ))
  ))
  (get-pep-search!)
  (get-autocomplete-lists!)
  (session/put! :current-page #'search-page ))


(defn update-pep-search! [k v] 
  ;(js/console.log "update!" (str k) (str v))
  (swap! pep-search-state assoc k v )

  ;set url -- so we can copy paste searches
  (aset (.-location js/window) "hash" (search-path {:query-params @pep-search-state} ) )

  ;(when (> (count (str v)) 3)  
     (js/console.log "searching:" (str v) (count v) )
     (get-pep-search!)
  ;)
)
(defn incoffset! []
  (swap! pep-search-state update-in [:offset]  #(+ 10 %))
  (get-pep-search!)
)
(defn updatesearch [pepkey dom]
 (js/console.log "update: " dom)
 (update-pep-search! pepkey (-> dom .-target .-value )  )
)
(defn select-dropdown [pepkey opts f]
  [:select {  :on-change #(f pepkey) }
   (map (fn[k] [:option {:key k} k]) opts)
  ]
)
(defn search-textfield [pepkey size]
    [:input {:type "text" 
             :size size
             :value (pepkey @pep-search-state)
             :on-change #(updatesearch pepkey %) }]
)

; STUDY: TODO: pull from db instead of hardcode
(defn study-dropdown [f]
  (select-dropdown :study ["" "RewardR01" "RewardR21" "MEGEmo" "CogR01"] #(f :study))
)

(defn pep-search-form []
  ;(get-pep-search!)
  ;INPUTS
  [:div 
    ; ID
    (search-textfield :eid 5)
    ; NAME
    (search-textfield :fullname 20)
    ; AGE
    (search-textfield :minage 2)
    (search-textfield :maxage 2)
    ;STUDY
    (study-dropdown updatesearch)
    ; HAND
    (select-dropdown :hand ["" "R" "L" "U"] updatesearch)
    ; Sex
    (select-dropdown :hand ["" "M" "F" "U"] updatesearch)
    ; OFFSET
    [:input {:type "button" :value (:offset @pep-search-state)
             :on-click incoffset! }]
 ]
)

(defn pep-list-comp []
  [:table  {:class "table table-striped table-condensed table-hover"} 
    [:thead [:tr (doseq [x  ["ids" "name" "info" "last visit" "nvisits"]] ^{:key (str "header" x) }[:th x]) ] ]
    [:tbody
     (doall (map render-person-row @pep))
    ]
   ]
)

(defn add-person! [doc]
 (def dob (apply tc/date-time (vals (select-keys (doc :dob ) [:year :month :day ] )) ))
 (def sendpdata (merge (select-keys doc [:fname :lname :sex :hand :source ])  {:dob (str dob)} ) )
 (js/console.log (str "sending" sendpdata) )
 (POST "/person"
       :keywords? true
       :format :json
       :response-format :json 
       :params sendpdata
       :handler (fn [response] 
            ; print 
            (js/console.log "response:" (type response) (str response) )

            ; TODO
            ; check response, append to error messagse
            (js/console.log "calling append-error state on above")
            (add-error-state! response)

            ; refresh search, hopefully the new guy is there
            ; MAYBE join first and last in the pep-search ?
            (get-pep-search!)
       )
  )
)

(defn add-person-comp []
  (let [
     ; use name form search
     ; break into first and last
     names (clojure.string/split (:fullname @pep-search-state) #"\s+" )
     doc (atom {:fname (get names 0) :lname (get names 1) :sex "" :dob {:year 2010 :month 1 :day 1} :hand ""})
    ]
    (fn []
     [:div 
       [bind-fields add-person-form doc]
       [:div.col-xs-4 [:div.btn.btn-default {:on-click #(add-person! @doc) } "Add Person"  ]]
     ]
    )
 ))

(defn search-comp []
  [:div ;[:h1 "LNCDWEB"]
   [:div {:class "search-cntnr"}
    ;[:div (str @pep-search-state) ]
    [:div (pep-search-form) ]
    [:div (pep-list-comp) ]
    (when (empty? @pep)
       [:div [(add-person-comp)] ]
    )
   ]
  ]
)

;; -------
;; Person/Visit

; person info
(defn person-info-comp []
 (def info (:info @person-state))
 (def dropinfo (get-in @person-state [:info :maxdrop]))

 [:div
  [:h2 (clojure.string/join " " (:ids info)) (str "(" (:pid @person-state) ")") ]
  [:h3 (str (:fname info) " " (:lname info)) ] 
  (when (not (nil? dropinfo)) [:div "Drop: " dropinfo ])
 ;[:div
 ;  (edn->hiccup (:info @person-state))
 ;]

])

; show tasks tasks -- link if has data
(defn visit-task-idv-comp [t]
 
 (def attr (if (:hasdata t) 
   (hash-map :on-click #(gotohash (str "/task/" (:vtid t) )  ) 
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

            (add-error-state! response)
            ; update visit list again
            (set-person-visits! (:pid @person-state))
       )
  )
)
(defn update-visit-has-note [how doc]
 (if (and (not(= how "cancel") ) (clojure.string/blank? (:note doc)))
   (add-error-state! {:warning (str "can not " how " need a note!")})
   (update-visit how doc)
 )
)


(defn visit-idv-actions [visit]
  ; TODO: use current visitdate as date
  (let [doc (atom {:note "" :date {} :vid (visit :vid) :ra "testRA" })] 
  (fn[]
      [:div.visitactions
       [bind-fields idv-vid-form doc]
       [:a {:on-click #(gotohash (str "#/visit/" (visit :vid) "/checkin" )) } "checkin" ]"|"
       [:a {:on-click #(update-visit-has-note "noshow" @doc) } "noshow"  ]"|"
       [:a {:on-click #(update-visit-has-note "cancel" @doc) } "cancel"  ]"|"
       [:a {:on-click #(update-visit-has-note "resched" @doc) } "resched" ]
      ]
  )
  )
)

;  
(defn visit-idv-comp [visit]
   
   (def scorecolor (colorspctm (visit :vscore)))
   ;(js/console.log "score: " scorecolor (visit :vscore))
   ^{:key (:vid visit)}[:li {:id (visit :vid) :class (str (visit :vstatus) " visititem " (visit :vtype) ) }

    ; DATE and AGE
    [:div {:class "visitdate"} 
       (str (notime-datestr (visit :vtimestamp)) " - " (roundstr (visit :age) 2) )
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
;(def bs-row [label id fieldtype]
;   [:div.row
;    [:div.col-md-2 [:label label]]
;    [:div.col-md-5
;     [:input.form-control {:field fieldtype :id id}]
;    ]]
;)

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
            (add-error-state! response)

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
                             :id :type :input-placeholder "TYPE"}] ]
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
                :vtype "Scan"
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


; person component: person, visits, contacts
(defn person-comp []
 [:div {:class "person"} 

   [:div {:class "person-info"}
      (person-info-comp )
   ]

   (when (have-person)
     [:div {:class "visit-add col-md-5"}
        [ (new-visit-form )  ]
     ]
   )

   [:div {:class "visit-cntnr"} 
   (map visit-idv-comp (:visits @person-state)) ]
 ]
)
;; -------- all combin
(defn pep-visit-comp []
 [:div 
   (msg-view-comp )
   (search-comp)
   (person-comp)
 ]
)

(defn search-page []
  (pep-visit-comp)
)

; ----- tasks
(def visit-task-state (atom [{:vtid 0}]))

(defn set-visit-task! [vtid]
  (js/console.log  vtid)
  (get-json (str "/visit_task/" vtid) 
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
  [:h3 (:fname t) " " (:lname t)  " › " (:task t) " " [:span {:class "id"} "@ " (roundstr (:age t) 1) " yro" ]   ]
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


;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to web4"]
   [:div "looks like it's working" ]
   [:div [:a {:href "#/search/1"} "go search"]]])

(defn person-page [vid]
  [:div [:h2 "Visits"]
   [:div [:a {:href "#/search/1"} "go to the home page"]]]
  (person-comp) )

(defn visit-task-page []
   (visit-task-comp)
)

(defn current-page []
  [:div [(session/get :current-page)]])


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
  (let [tasks    (:tasks @autocomplete-lists)
        studys   (map :study (:studys @checkin-data))
        vtype    (:vtype @checkin-data)
       ]
  (flatten (doall (map #(set-current-tasks % vtype  tasks) studys)  ))
  ;(js/console.log (str curtasks))
  ;(flatten curtasks)

))
; get tasks matching some text
(defn task-source [text]
 (distinct (filter #(-> % (.toLowerCase) (.indexOf text) (> -1)) (map #(:task %) (:tasks @autocomplete-lists))))
)
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
                         :data-source task-source
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
        (-> @checkin-data :age (roundstr 1) str) "yo "
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

;; --------
;; test reagent form
;; ----
(def formtest 
 [:div 
  [:input.form-control {:field :numeric :fmt "%.2f" :id :testf} ]
 ]
)


(secretary/defroute "/add/visit/:pid" [pid]
  (set-person-visits! pid)
  (session/put! :current-page #'person-comp))
;; ---

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")



(secretary/defroute "/" []
  (get-pep-search!)
  (get-autocomplete-lists!)
  (session/put! :current-page #'search-page))
 ; (session/put! :current-page #'home-page))

; VISIT
(secretary/defroute "/person/:pid" [pid]
  (js/console.log "routing to person/pid with " pid)
  (set-person-visits! pid)
  (get-autocomplete-lists!)
  (session/put! :current-page #'person-page))

; visit CHECKIN
(secretary/defroute "/visit/:vid/checkin" [vid]
  (js/console.log "routing to person/pid with " vid)
  (get-autocomplete-lists!)
  (set-visit! vid)
  (session/put! :current-page #'checkin-page)
)
; TASK
(secretary/defroute "/task/:vtid" [vtid]
  (js/console.log vtid)
  (set-visit-task!  vtid)
  (session/put! :current-page #'visit-task-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
