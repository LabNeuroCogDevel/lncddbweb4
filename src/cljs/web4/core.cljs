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
              [web4.search :as search :refer [ search-page ] ]

    )
    (:import goog.History))


;;; settings for postgrest haskell server
(def pgresturi "http://127.0.0.1:3001/")




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
  ;; does not exist, now included in component
  ;(search/get-pep-search!) 
  ;; no longer needed?
  ;(add-watch search/pep-search-state :phonehome search/search-pep!)
  (h/get-autocomplete-lists!)
  (session/put! :current-page #'search-page))
 ; (session/put! :current-page #'home-page))


; SINGLE PERSON: VISITS
(secretary/defroute "/person/:pid" [pid]
  (js/console.log "routing to person/pid with " pid)
  (v/set-person-visits! pid)
  (h/get-autocomplete-lists!)
  (session/put! :current-page #'person-page))

; visit CHECKIN
(secretary/defroute "/visit/:vid/checkin" [vid]
  (js/console.log "routing to person/pid with " vid)
  (h/get-autocomplete-lists!)
  (checkin/set-visit-checkin! vid)
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
