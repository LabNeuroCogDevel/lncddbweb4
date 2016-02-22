(ns web4.search
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]

              ;get data
              [ajax.core :refer [GET POST] ]

              [secretary.core :as secretary :include-macros true]

              [reagent-forms.core :refer [bind-fields init-field value-of]  ]

              [clojure.data :refer [diff]]

              ; use data
              [cljs-time.core :as tc ]
              [cljs-time.format :as tf ]

              ;debug show data
              [json-html.core :refer [edn->hiccup]]

              ; helping functions -- can't do this or we recusive forver
              [ web4.helpers :as h ]
              [ web4.messages :as m ]
              [ web4.visits  :as v  :refer [person-comp]  ]
              
    ))


;--- people
; start out with a people search result
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




;(declare search-page)
;(declare search-pep!)

; SEARCH ROUTE -- here b/c search-path needs to be defined before update-pep-search!
;(secretary/defroute search-path "/search" [query-params]
;  ;(js/console.log (pr-str  query-params) query-params)
;
;  ; get all the query params from the url,
;  ; use those to override whatver search state is
;  (doseq [pk (keys @pep-search-state)] 
;    (let [v (pk query-params)]
;    (when (not(nil? v))
;     (swap! pep-search-state assoc pk v ))
;  ))
;
;  ; get people
;  (get-pep-search!)
;  (add-watch pep-search-state :phonehome search-pep!)
;  (h/get-autocomplete-lists!)
;  (session/put! :current-page #'search-page ))
;

(def search-form 
 [:div
   [:div.row
    [:div.col-xs-3 [:input.form-control  {:field :text :id :eid       :placeholder "10931"}]]
    [:div.col-xs-5 [:input.form-control  {:field :text :id :fullname :placeholder "Bart Simpson"}]]
   ]
   [:div.row
    ;[:div.col-xs-4 [:select.form-control {:field :list :id :study} 
    ;     [:option {:key ""} ""]
    ;     [:option {:key "CogR01"} "CogR01"]
    ;     (for [s (:studies @h/autocomplete-lists) ]
    ;       ^{:key (str "opt-" s)}[:option {:key s}  s]
    ;     )
    ;]]
    [:div.col-xs-4 [:div {:field :typeahead 
                             :input-class "form-control"
                             :data-source #(h/autocomplete-source :studies str %)
                             :list-class "typeahead-list"
                             :item-class "typeahead-item"
                             :highlight-class "highlighted"
                             :id :study :input-placeholder "STUDY"}] ]
    
    ;[:div.col-xs-2 [:select.form-control {:field :list :id :hand} 
    ;     ;[:option {:key ""} "HDN"]
    ;     (for [hand ["" "R" "L" "U" "A"]] [:option {:key hand} hand] )
    ;]]
    [:div.col-xs-2 [:select.form-control {:field :list :id :sex} 
         ;[:option {:key ""} "SEX"]
         (for [sex ["" "M" "F" "U"]] [:option {:key sex} sex] )
    ]]
    [:div.col-xs-2 [:input.form-control  {:size 2 :field :numeric :id :minage }]]
    [:div.col-xs-2 [:input.form-control  {:size 2 :field :numeric :id :maxage }]]
   ]
 ]
)






(defn add-person! [doc]
 (js/console.log "addperson!")
 (def dob (apply tc/date-time (vals (select-keys (doc :dob ) [:year :month :day ] )) ))
 (def sendpdata (merge (select-keys doc [:fname :lname :sex :hand :source ])  {:dob (str dob)} ) )
 (js/console.log (str "sending" sendpdata) )
 (POST "person"
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
            (m/add-error-state! response)

            ; TODO
            ; refresh search, hopefully the new guy is there
            ; MAYBE join first and last in the pep-search ?
            ;(get-pep-search!)
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



;------------- try again

(defn update-search-url-rt! [state routefn]
  ;set url -- so we can copy paste searches
  ;(aset (.-location js/window) "hash" (routefn {:query-params state} ) )
  (js/console.log "[would be] update search--switching to " (routefn {:query-params state}  ))
  ;(js/console.log "at "  (.-location js/window) )
)

(defn search! [params results routefn]
 ;(let [url (ajax.core/uri-with-params "/people" params) ] ;broke with update?
 (let [url (str "people?" (ajax.core/params-to-str params)) ]
  (js/console.log "search pep with: " (str url) 
                  "\nurl: " url )
  (h/get-json url
    (fn[r] (when (not(nil? (:data r))) 
            (reset! results (:data r)
            (update-search-url-rt! params routefn)
 ))))
))


(defn peprow [selectedatom si]
 (let [isselected (= (:pid @selectedatom) (:pid si))]
 ^{:key (:pid si)} 
  [:tr  { :on-click #(reset! selectedatom si)
          :class (str "drop-" (:maxdrop si)
                  (when isselected " search-selected"))
         }

   [:td (for [id (:ids si)]  ^{:key (str si  id)}[:div {:class "search-id"} id ] )]
   [:td [:div (:fname si) " " (:lname si) ]
       [:div {:class "dob"} (h/notime-datestr (:dob si)) ]
   ]
   [:td {:class "monospaced"}
         (h/roundstr     (:curage si ) 1 ) " " 
         (:sex si ) " " 
         (:hand si) ]
   [:td (h/notime-datestr (:lastvisit si))]
   [:td (:numvisits si)]
   ;[:td (str si) ]
   ;[:td (first (si :ids)) ]
]))



(defn search-person-add-comp [fullname]
  (let [
     ; use name form search
     ; break into first and last
     names (clojure.string/split (or fullname  "new person" ) #"\s+" )
     fname (get names 0)
     lname (get names 1)
     doc (atom {:fname fname :lname lname :sex "" 
                :dob {:year 2010 :month 1 :day 1} :hand ""})
    ]
    ;(js/console.log "add-person-comp" names)
    (fn []
     [:div 
       [bind-fields add-person-form doc]
       [:div.col-xs-4 [:div.btn.btn-default {:on-click #(add-person! @doc) } "Add Person"  ]]
     ]
    )
 ))

;check what we care about
(defn search-really-changed? [prev curr]
 (let [keylist [:eid :study :hand :sex :minage :maxage :fullname]
        prev   (select-keys prev keylist) 
        curr   (select-keys curr keylist) 
        pcdiff (diff curr prev) 
      ]
  ;(js/console.log "did we change?!\n\tprv:" (str prev) "\n\tcur: "  (str curr) "\n\tdiff: "  (str pcdiff) "\n\t?" )
  (not(nil? (first pcdiff) ))
))
; need to know we will have the search route
(declare searchonlyrt)
(defn search-comp-new [params]
  (let [
      defform    {:study "" :hand "" :sex "" :minage 0 :maxage 99} 
      searchform (atom (select-keys (merge defform params) (keys defform)))
      peps       (atom [{:pid 0 :fname "SEARCHING"}]) 
      selected   (atom {})
  ]
 ; run search 
 (search! @searchform peps searchonlyrt)
 ; rturn function
 (fn[]
  (add-watch searchform :updatesearch (fn[w s p n] (when (search-really-changed? p n) 
     (search! n peps searchonlyrt))))
  (add-watch selected   :updatevisit  (fn[w s p n] (v/set-person-visits! (:pid n) )))

  [:div {:class "search-cntnr"}

    [bind-fields search-form searchform ]

    [:div 
     [:table  {:class "table table-striped table-condensed table-hover"} 
       [:thead [:tr (for [x  ["ids" "name" "info" "last visit" "nvisits"]] ^{:key (str "header" x) }[:th x]) ] ]
       [:tbody
       (doall (map #(peprow selected %)  @peps))
      ]] 

     ; -- add person option when no person matches search params
     (when (empty? @peps)
        [:div [search-person-add-comp (:fullname @searchform)] ]
     )
    ]
  ]
)))

(defn clicked [e n doc]
  (js/console.log e)
  (js/console.log n)
  
  (js/console.log (str @doc)
  )
)
(defn search-page [params]
 [:div 
  [m/msg-view-comp]
  [search-comp-new params]
  [:div.col-md-5
   [v/person-comp]
  ]
 ]
)


(secretary/defroute searchonlyrt "search" [query-params]
 (h/get-autocomplete-lists! )
 (session/put! :current-page #(search-page query-params )) )
