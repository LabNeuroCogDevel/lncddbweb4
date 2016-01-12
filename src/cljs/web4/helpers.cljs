(ns web4.helpers
    (:require [reagent.core :as reagent :refer [atom]]

              ;get data
              [ajax.core :refer [GET POST] ]

              [secretary.core :as secretary :include-macros true]

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
              
              ; get-json adds to error
              [web4.messages :as m]

              
    ))


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

; --- help that depends on error state
; get has the same call
; most places
(defn get-json [url handlefn]
   (GET url :keywords? true :response-format :json 
            :handler (fn[r]  (do (m/add-error-state! r) (handlefn r)) ))
     
)


; ---- things to autocomplete
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


; get tasks matching some text
(defn task-source [text]
 (distinct (filter #(-> % (.toLowerCase) (.indexOf text) (> -1)) (map #(:task %) (:tasks @autocomplete-lists))))
)

; see also :drakula @ https://github.com/rm-hull/inkspot
;(def colorspctm (cc/gradient :red :green 10) )
(defonce colorspctm (cc/color-mapper (cc/ui-gradient :miaka 10) 0 5))
