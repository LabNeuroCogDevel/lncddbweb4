(ns web4.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]

              ;get data
              [ajax.core :refer [GET POST] ]
    )
    (:import goog.History))

;; -------------------------
;; Models?

(defn render-row [si]
 ^{:key (:pid si)} 
  [:tr
   [:td (si :fname) " " (si :lname) ]
   [:td (si :age) ]
   [:td (si :dob) ]
   [:td (si :sex) ]
   [:td (si :hand) ]
   ;[:td (first (si :ids)) ]
])

; start out with a people search result
(def pep (atom [{:pid 0 :fname "Searching" :lname "For People" } ] ))
(def pep-search-state (atom {:minage 0 :maxage 100 :name "Will" :sex "" :hand "" :nvisit 0 } ))


(defn get-pep-search! []
  (GET (str "/lists?n=" (:name @pep-search-state )) 
       :keywords? true 
       :response-format :json 
       :handler (fn [response] 
            (js/console.log "response:" (type response) (str response) )
            (reset! pep response)
       )
  )
)

(defn update-pep-search! [k v] 
  (js/console.log "update!" (str k) (str v) )
  (swap! pep-search-state assoc k v )
  (get-pep-search!)
)

(defn pep-search-comp []
  [:input {:type "text" 
           :value (:name @pep-search-state)
           :on-change #(update-pep-search! :name (-> % .-target .-value ) )}]
)
(defn pep-list-comp []
  [:table 
    (map render-row @pep)
   ]
)

(defn pep-comp []
  [:div [:h1 "LNCDWEB"]
    [:div (str @pep-search-state) ]
    [:div (pep-search-comp) ]
    [:div (pep-list-comp) ]
  ]
)
;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to web4"]
   [:div [:a {:href "#/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About web4"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

(secretary/defroute "/list" [n]
  (session/put! :current-page #'pep-comp ))

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
