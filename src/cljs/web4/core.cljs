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


;;; helpers
;(def colorspctm (cc/gradient :red :green 10) )
(def colorspctm (cc/color-mapper (cc/ui-gradient :miaka 10) 0 5))
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

;--- visit

; contains all the visits
(def person-state (atom {:pid 0 :visits []}))
; contains how to display the visits
(def visit-state-display (atom {:collapsed false}))

(defn set-person-visits! [pid]
  (js/console.log  pid)
  (GET (str "/person/" pid "/visits" ) 
       :keywords? true 
       :response-format :json 
       :handler (fn [response] 
            (js/console.log "response:" (type response) (str response) )
            (swap! person-state assoc :pid pid)
            (swap! person-state assoc :visits response)
       )
  )
)

;--- note
(defn render-note [ni]
 ^{:key (:nid ni)} 
 [:div 
   [:div  (:note ni) " - " (:ra ni) " " [:span {:class "id"} (:nid ni) ]   ]
 ]
)

;--- people
; start out with a people search result
(def pep (atom [{:pid 0 :fname "Searching" :lname "For People" } ] ))
(def pep-search-state (atom {:study "" :eid "" :hand "" :fullname "" :sex "" :mincount 0 :minage 0 :maxage 200 :offset 0 :selected-pid 0}))
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
  [:tr  { :on-click #(select-person! (:pid si))
          :class (str "drop-" (:maxdrop si)
                  (when (= (:selected-pid @pep-search-state) (:pid si) )
                        " search-selected")) }

   [:td (map (fn[id] ^{:key (str si  id)}[:div {:class "search-id"} id ]) (:ids si) ) ]
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
  (GET (ajax.core/uri-with-params "/people" @pep-search-state) 
  ;(GET (str "/people?fullname=" (:fullname @pep-search-state )) 
  ;(GET (ajax.core/uri-with-params "/lists" pep-search-state) 
       :keywords? true 
       :response-format :json 
       :handler (fn [response] 
            (js/console.log "response:" (type response) (str response) )
            (reset! pep response)
       )
  )
)

(defn update-pep-search! [k v] 
  ;(js/console.log "update!" (str k) (str v))
  (swap! pep-search-state assoc k v )

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
    [:thead [:tr (map (fn[x] ^{:key (str "header" x) }[:th x]) ["ids" "name" "info" "last visit" "nvisits"]) ] ]
    [:tbody
     (map render-person-row @pep)
    ]
   ]
)

(defn search-comp []
  [:div ;[:h1 "LNCDWEB"]
   [:div {:class "search-cntnr"}
    ;[:div (str @pep-search-state) ]
    [:div (pep-search-form) ]
    [:div (pep-list-comp) ]
   ]
  ]
)


;; -------
;; Visit

; show tasks tasks -- link if has data
(defn visit-task-idv-comp [t]
 ^{:key (:vtid @t)}
 (def attr (if (:hasdata t) 
   (hash-map :on-click #(gotohash (str "/task/" (:vtid t) )  ) 
             :class "visittask link"
   )
   (hash-map :class "visittask" )
 ))
 [:div attr (:task t) ]
)

;  
(defn visit-idv-comp [visit]
   ^{:key (:vid visit)}
   (def scorecolor (colorspctm (visit :vscore)))
   (js/console.log "score: " scorecolor (visit :vscore))
   [:li {:id (visit :vid) :class (str (visit :vstatus) " visititem " (visit :vtype) ) }

    ; DATE and AGE
    [:div {:class "visitdate"} 
       (str (notime-datestr (visit :vtimestamp)) " - " (roundstr (visit :age) 2) )
       [:div {:class "id"} (visit :vid) ] ]
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

; visit-form via reagent-forms
(defn visit-form-date []
  [:div
   [:input.form-control {:field :text :id :study}]
   [:input.form-control {:field :text :id :visitday}]

   [:div.row
    [:div.col-md-2 [:label "VisitDay"]]
    [:div.col-md-5
     [:div
      {:field :datepicker :id :visitday :date-format "yyyy/mm/dd" :inline true}]]]

  ]
   ;[:input.form-control {:field :datepicker :id :visitday :date-format "yyyy-mm-dd" :inline true}]
)
;visit-form
(defn new-visit-form [pid]
      (let [new-visit-state (atom {:visitday {:year 2016 :day 01 :month 01}  :time "00:00" :study 'none } )]
      [:div.new-visit-form
        ;[:input {:type 'text :name 'datetime} ] [:br]
        ;[pikaday/date-selector {:date-atom new-visit-date }]
        [bind-fields visit-form-date new-visit-state]

        ;[:div {:field :datepicker :id :visitday :date-format "yyyy-mm-dd" :inline true}]

        (study-dropdown nil)
        (select-dropdown :dur ['0.5 '1 '1.5 '2 ] #(println "dur:" %)) [:br]
        [:textarea { :name 'note :defaultValue  "Notes" } ] 
        [:div (edn->hiccup @new-visit-state) ]
      ]) 
)

; person component: person, visits, contacts
(defn person-comp []
 [:div {:class "person"} 
   [:div {:class "person-info"}
     ;TODO:
   ]
   (when (and (:pid @person-state) (> (:pid @person-state) 0) )
     [:div {:class "visit-add col-md-5"}
        ;(str @person-state)
         (new-visit-form (:pid @person-state) ) 
     ]
   )
   [:div {:class "visit-cntnr"} 
   (map visit-idv-comp (:visits @person-state)) ]
 ]
)

; ----- tasks
(def visit-task-state (atom [{:vtid 0}]))

(defn set-visit-task! [vtid]
  (js/console.log  vtid)
  (GET (str "/visit_task/" vtid  ) 
       :keywords? true 
       :response-format :json 
       :handler (fn [response] 
            (js/console.log "response:" (type response) (str response) )
            (reset! visit-task-state response)
       )
  )
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

;; -------- all combin
(defn pep-visit-comp []
 [:div 
   (search-comp)
   (person-comp)
 ]
)

(defn search-page []
 (pep-visit-comp)
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

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))


; SEARCH
(secretary/defroute "/search/:n" [n]
  ;(set-search-page! n)
  (get-pep-search!)
  (session/put! :current-page #'search-page ))

; VISIT
(secretary/defroute "/person/:pid" [pid]
  (js/console.log pid)
  (set-person-visits! pid)
  (session/put! :current-page #'person-page))

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
