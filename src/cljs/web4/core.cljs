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
              
              ; other components
              [web4.task :as vt :refer [visit-task-page] ]
              [web4.helpers  :as h]
              [web4.messages  :as m]
              [web4.visits  :as v  :refer [person-comp]  ]
              [web4.addstudy :refer [ addstudy-page ] ]
              [web4.checkin :as checkin :refer [ checkin-page ] ]

    )
    (:import goog.History))


;;; settings for postgrest haskell server
(def pgresturi "http://127.0.0.1:3001/")

;;; helpers
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
  (v/set-person-visits! pid)
  ; add selected to this one
  ; remove selected from anyone else
  ; -- update state, updates css
  (swap! pep-search-state assoc :selected-pid pid)
  ; -- update each vector
  ;(swap! pep set-select-on-pid pid pep)
)

(defn render-person-row [si]
 ^{:key (:pid si)} 
  ;[:tr  { :on-click #(v/set-person-visits! (:pid si))}
  ; TODO WARNING 'when' on @pep-search-state causes warning
  [:tr  { :on-click #(select-person! (:pid si))
          :class (str "drop-" (:maxdrop si)
                  (when (= (:selected-pid @pep-search-state) (:pid si) )
                        " search-selected")) }

   [:td (doseq [id (:ids si)]  ^{:key (str si  id)}[:div {:class "search-id"} id ] )]
   [:td [:div (:fname si) " " (:lname si) ]
       [:div {:class "dob"} (notime-datestr (:dob si)) ]
   ]
   [:td {:class "monospaced"}
         (roundstr     (:curage si ) 1 ) " " 
         (:sex si ) " " 
         (:hand si) ]
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
            (js/console.log "response:" (type response) (str response) )
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
(declare search-pep!)

; SEARCH ROUTE -- here b/c search-path needs to be defined before update-pep-search!
(secretary/defroute search-path "/search" [query-params]
  ;(js/console.log (pr-str  query-params) query-params)

  ; get all the query params from the url,
  ; use those to override whatver search state is
  (doseq [pk (keys @pep-search-state)] 
    (let [v (pk query-params)]
    (when (not(nil? v))
     (swap! pep-search-state assoc pk v ))
  ))

  ; get people
  (get-pep-search!)
  (add-watch pep-search-state :phonehome search-pep!)
  (h/get-autocomplete-lists!)
  (session/put! :current-page #'search-page ))

(defn update-search-url! [state]
  ;set url -- so we can copy paste searches
  (aset (.-location js/window) "hash" (search-path {:query-params state} ) )
)

(defn search-pep! [watchkey doc prev now]
  (js/console.log "search pep with now" (str now) )
  (get-json (ajax.core/uri-with-params "/people" now) 
    (fn[r] (when (not(nil? (:data r))) 
            (reset! pep (:data r)
            (update-search-url! now) ))))
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
  [:select {  :on-change #(f pepkey) 
              :on-blur  update-search-url!}
   (map (fn[k] [:option {:key k} k]) opts)
  ]
)
(defn search-textfield [pepkey size]
    [:input {:type "text" 
             :size size
             :value (pepkey @pep-search-state)
             :on-change #(updatesearch pepkey %)
             :on-blur  update-search-url!}]
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
    (select-dropdown :sex ["" "M" "F" "U"] updatesearch)
    ; OFFSET
    [:input {:type "button" :value (:offset @pep-search-state)
             :on-click incoffset! }]
 ]
)

(def search-form 
 [:div
   [:div.row
    [:div.col-xs-2 [:input  {:field :text :id :eid       :placeholder "10931"}]]
    [:div.col-xs-4 [:input  {:field :text :id :fullname :placeholder "Bart Simpson"}]]
    [:div.col-xs-1 [:input  {:size 2 :field :numeric :id :minage }]]
    [:div.col-xs-1 [:input  {:size 2 :field :numeric :id :maxage }]]
   ]
   [:div.row
    [:div.col-xs-4 [:select.form-control {:field :list :id :study} 
         [:option {:key :CogR01} "CogR01"]
    ]]
    [:div.col-xs-2 [:select.form-control {:field :list :id :hand} 
         (for [hand ["" "R" "L" "U"]] [:option {:key hand} hand] )
    ]]
    [:div.col-xs-2 [:select.form-control {:field :list :id :sex} 
         (for [sex ["" "M" "F" "U"]] [:option {:key sex} sex] )
    ]]
   ]
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
 (js/console.log "addperson!")
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
     names (clojure.string/split (or (:fullname @pep-search-state)  "new person" ) #"\s+" )
     doc (atom {:fname (get names 0) :lname (get names 1) :sex "" :dob {:year 2010 :month 1 :day 1} :hand ""})
    ]
    ;(js/console.log "add-person-comp" names)
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
    [bind-fields search-form pep-search-state ]
    ;;[:div (pep-search-form) ]
    [:div (pep-list-comp) ]
    ;(when (empty? @pep)
    ;   [:div [(add-person-comp)] ]
    ;)
   ]
  ]
)

;; -------- all combin
(defn pep-visit-comp []
 [:div 
   (m/msg-view-comp )
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

(defn person-page []
  [:div [:h2 "Visits"]
   [:div [:a {:href "#/search/1"} "go to the home page"]]]
  (person-comp) )


(defn current-page []
  [:div [(session/get :current-page)]])



;; --------
;; test reagent form
;; ----
(def formtest 
 [:div 
  [:input.form-control {:field :numeric :fmt "%.2f" :id :testf} ]
 ]
)


(secretary/defroute "/add/visit/:pid" [pid]
  (v/set-person-visits! pid)
  (session/put! :current-page #'person-comp))
;; ---

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

;(defn watch-pep-url []
; (select-keys (merge 
;                   {:fullname "" :eid "" :study "" :hand "" :sex "" :minage 0 :maxage 99 :selected ""} 
;                   (secretary/decode-query-params (.-location js/window))
;                  )
;                 [:fullname :eid :study :hand :sex :minage :maxage :selected ]   ))]
; (js/console.log "initial component with href" (.-location js/window ))
;)
;)

(secretary/defroute "/" []
  (get-pep-search!)
  (h/get-autocomplete-lists!)
  (add-watch pep-search-state :phonehome search-pep!)
  (session/put! :current-page #'search-page))
 ; (session/put! :current-page #'home-page))

; VISIT
(secretary/defroute "/person/:pid" [pid]
  (js/console.log "routing to person/pid with " pid)
  (v/set-person-visits! pid)
  (h/get-autocomplete-lists!)
  (session/put! :current-page #'person-page))

; visit CHECKIN
(secretary/defroute "/visit/:vid/checkin" [vid]
  (js/console.log "routing to person/pid with " vid)
  (h/get-autocomplete-lists!)
  (v/set-visit! vid)
  (session/put! :current-page #'checkin-page)
)



; add study
(secretary/defroute "/study" []
  ;(session/put! :current-page #'home-page)
  (session/put! :current-page #'addstudy-page)
)


;; TASK
(secretary/defroute "/task/:vtid" [vtid]
  (js/console.log vtid)
  (vt/set-visit-task!  vtid)
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
