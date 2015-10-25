(ns web4.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env] ]
            
            
            ; extend jdbc to suport pgsql json type
            ;[web4.jdbcjson]
            ; sql queries in their own file
            [yesql.core :refer [defqueries]]
            ; send json out
            [cheshire.core :as json]
            ; work between types
            [clojure.data.json :as cjson]

            ; google cal

           ))

;;;; DB
(def db-spec {:classname "org.postgresql.Driver"
 :subprotocol "postgresql"
 :subname "//localhost:5432/lncddb"
 :user "postgres"})

; define queries 
;  * list-people-by-name
;  * list-people-by-name-study-enroll
(defqueries "sql/people.sql" {:connection db-spec})
;  * list-visits-by-pid
(defqueries "sql/visits.sql" {:connection db-spec})

;; http://blog.00null.net/clojure-yesql-and-postgesql-arrays/
; posgresql arrays 
;
; (defn get-snippet [snippet-id]
;   (jdbc/with-db-transaction [conn db-spec]
;       (let [row (first (-get-snippet conn snippet-id))]
;             (update-in row [:tags] (fn [ts] (vec (.getArray ts)))))))

;;;;

(defn listsubj 
  "list of all subjects matching subjname"
  [subjname]
  ;(html5 (list-people-by-name db-spec subjname ))
  (println subjname (list-people-by-name {:name subjname}  )  )
  (list-people-by-name {:name subjname}  )
)

 
(defn pep-search
  "search subjects and get visit summaries"
  [searchmap]
  ; by default we want these things
  (def defsearch {:study "%" :etype "%" :hand "%" :fullname "%" :sex "%" :mincount 0 :minage 0 :maxage 200 :offset 0})

  ; so merge what we actually want (and remove any extra requests)
  (def mergesearch (merge defsearch (select-keys searchmap  (keys defsearch) ))   )
  ;(println "merged" mergesearch)

  ; some are strings we want them to be numeric, maybe do this in sql?
  (def search (reduce (fn [m k] (update-in m [k] #(Integer. %))  ) mergesearch   [:mincount :minage :maxage :offset] ) )
  (println search)

  ; what is the type of our data
  ;(doall (for [k (keys search)] (println (str k " " (type (search k) ) ) )  )  )
  
  ; push hasmap to sql query from yesql function
  (def res (list-people-by-name-study-enroll search))

  ;(println  res ) 
  res
  
  ; old non-json display
  ;(html5 (list-people-by-name db-spec subjname ))
)
(defn pgsql-obj-to-str
 [k o]
 (println "o: " o)
 (map (fn [v] 
   ;(println "kv: "  (k v) )
   ;(println "kv type: "  (type (k v)) )
   (update-in v [k] 
                 #(if (or (not (some? %) )
                          (= (.toString %) "null") )
                  {}
                  (cjson/read-str  (.toString %))))) o) 
)

(defn visit-search
  "look for visits given a pid"
  [pid]
  ;TODO check pid is given, is integer
  
  ; get all visits from this person
  ;add notes and tasks
   (map (fn [r] 
          ;(println "row: " r)
          (def vid (Integer. (:vid r) ) )
          ;(println "vid: " vid)

          ; TODO: will be a problem when we also have files
          (def tasks (pgsql-obj-to-str :measures (list-visit_tasks {:vid vid}) ))
          ;(def tasks (list-visit_tasks {:vid vid}) )
          (println "tasks: " tasks)

          (conj {:tasks tasks }
                {:notes (list-visit_notes {:vid vid})} 
                r ) ) 
    (list-visits-by-pid {:pid (Integer. pid)})    )

  ;(->> (list-visits-by-pid {:pid (Integer. pid)})    
  ;     (map  #(conj {:tasks (list-visit_tasks {:vid (:vid %)} )}
  ;                  {:notes (list-visit_notes {:vid (:vid %)} )} 
  ;                  % ) ) )
) 

(defn sql-add-error 
 "run yesql function 'sqlf' with 'params', return hasmap that has error
  null if no error, error: str exception if there is"
 [sqlf params]
 (println "* sql-to-submit: " params)
 (try 
    (conj {:error nil} (sqlf params ) )
    (catch Exception e (hash-map :error (str e)  ) ) 
 )
)

(defn person-enroll [params] 
 (sql-add-error person-enroll<! (select-keys params [:pid :etype :id]) )
)
(defn match-or-U [p s]
  (clojure.string/upper-case(or (and (some? s) (re-matches p s)) "U") )
)

(defn pep-add [params] 
  (println "in: " params)
  (def params-keys [:fname :lname :dob :source :sex :hand])
  (def params-nonil (reduce (fn [m k] (update-in m [k] #(or % "none")  )) params params-keys   ) )
  (println "no nil: " params-nonil)
  ; make sure sex and hand are okay  MFU and LRU 
  (def submitparams 
   (merge (select-keys params-nonil params-keys) 
    {:sex  (match-or-U #"(?i)^[MF]$" (:sex  params))
     :hand (match-or-U #"(?i)^[RL]$" (:hand params))})
  ) 

  (println "final: " params-nonil)
  (sql-add-error person-add<! submitparams)
  
)

(defn person-edit   [params] 
 (println params)
)
(defn visit-add     [params] 
 (println params)
)
(defn visit-checkin [params] 
 (println params)
)


(def home-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      (include-js "js/app.js")]]]))

;; return json
(defn json-response [data & [status]]
    {:status  (or status 200)
    :headers {"Content-Type" "application/hal+json; charset=utf-8"}
    :body    (json/generate-string data)})


(defroutes routes
  (GET "/" [] home-page)

  ;;;;TODO: nest?

  ;;; people
  ; search
  ;http://0.0.0.0:3000/people/name?n=william
  (GET  "/people/name" [n] (json-response (listsubj n) ))
  (GET  "/people" {params :params} (json-response  (pep-search params) ))
  ; add
  (POST "/people" {params :params} (json-response  (pep-add    params) ))
  ; enroll 
  (POST "/person/:pid/enroll" {params :params}  (json-response (person-enroll  params) ))
  ; edit
  (POST "/person/:pid/edit" {params :params} (json-response (person-edit  params) ))

  ;;visits
  (GET "/person/:pid/visits" [pid] (json-response (visit-search pid) ))
  ; schedule
  (POST "/person/:pid/visit" {params :params} (json-response (visit-add    params) ))
  ; check in  -- select tasks, score visit
  (POST "/visit/:vid/checkin" {params :params} (json-response (visit-checkin params) ))

  ;; visit task
  ; edit
  ; view

  ;; task
  ; add, edit, remove
  ;; study
  ; add, edit, remove

  ; defaults
  (resources "/")
  (not-found "Not Found"))

; remove anti-forgery so we can curl as we please
(def my-site-defaults (assoc-in site-defaults [:security :anti-forgery] false )  )

(def app
  (let [handler (wrap-defaults #'routes my-site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
