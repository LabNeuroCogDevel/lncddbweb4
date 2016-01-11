;  add-error-state! 
;  msg-view-comp 
(ns web4.messages
    (:require [reagent.core :as reagent :refer [atom]]

              ;get data
              [ajax.core :refer [GET POST] ]

              [secretary.core :as secretary :include-macros true]

              [reagent-forms.core :refer [bind-fields init-field value-of]  ]

              ;debug show data
              [json-html.core :refer [edn->hiccup]]

              ; helping functions -- can't do this or we recusive forver
              ;[ web4.helpers :as h ]
              
    ))

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




