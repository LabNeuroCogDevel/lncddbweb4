(ns web4.addstudy
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

              ; helping functions
              [ web4.helpers :as h ]
              [ web4.messages :as m ]
              
    ))

(def study-form 
 [:div.row [:input.form-control {:field :text :id :test :placeholder "THIS IS A FORM"}] ]
)
(defn submit-study [doc]
  (js/console.log (str doc))
  (m/add-error-state! {:error "TEST"})
)

(defn study-comp []
 (let [doc (atom {:test ""})] 
  (fn[]
   [:div.addstudy
     [:h1 "add study" ]
     [bind-fields study-form doc]
     [:a {:on-click #(submit-study @doc)} "submit study"]
   ]
 ))
)

(defn addstudy-page []
 [:div 
  [:div (m/msg-view-comp )]
  [study-comp ]
 ]
)
