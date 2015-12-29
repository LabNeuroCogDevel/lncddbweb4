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
            ; json <-> PGobject 
            [web4.jdbcjson]
            ; work between types
            [clojure.data.json :as cjson]

            ; google cal
            ;[google-apps-clj :as gcal]

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
;  * list-tasks-by-vid
;  * get-visit-task-by-id
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
  (println "listsubj for name: " subjname)
  ;(println subjname (list-people-by-name {:name subjname}  )  )
  (list-people-by-name {:name subjname}  )
)

 
(defn pep-search
  "search subjects and get visit summaries"
  [searchmap]
  ; by default we want these things
  (def defsearch {:study "%" :eid "%" :etype "%" :hand "%" :fullname "%" :sex "%" :mincount 0 :minage 0 :maxage 200 :offset 0})
  (def mparms (merge defsearch (select-keys searchmap  (keys defsearch) ))  )

  (println "have: " mparms) 

  ; earlier invocation
  ;(def mergesearch (merge defsearch (select-keys searchmap  (keys defsearch) ))   )
  ;(def search (reduce (fn [m k] (update-in m [k] #(Integer. %))  ) mergesearch   [:mincount :minage :maxage :offset] ) )
  ;(println search)

  ; make numbers where we should have numbers
  (def search-int 
     (reduce (fn [x y] (update-in x [y]  #(Integer. %) )) mparms [:mincount :minage :maxage :offset] )
  )
  (def search
     (reduce (fn [x y] (update-in x [y]  #(if (nil? %) "%" %) )) search-int [:study :etype :hand :fullname :sex] )
  )

  (println "searching: " search)
  
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

; add tasks to visits
(defn task-lookup [v]
  ; all notes
  (def nt (list-notes-by-vid {:vid (:vid v)}))
  ; all tasks
  ;(def ts (map #(:task %) (list-tasks-by-vid {:vid (:vid v)})))
  (def ts (list-tasks-by-vid {:vid (:vid v)}))

  (println (v :vid) (count nt) (count ts) )

  (assoc (assoc v :tasks ts ) :notes nt)
)

(defn visit-search
  "look for visits given a pid"
  [pid]
  ;TODO checks
  (map task-lookup
       (list-visits-by-pid {:pid (Integer. pid)} )
  )
  ; BEFORE working psql->json
  ;   =======
  ;     ;TODO check pid is given, is integer
  ;     
  ;     ; get all visits from this person
  ;     ;add notes and tasks
  ;      (map (fn [r] 
  ;             ;(println "row: " r)
  ;             (def vid (Integer. (:vid r) ) )
  ;             ;(println "vid: " vid)
  ;   
  ;             ; TODO: will be a problem when we also have files
  ;             (def tasks (pgsql-obj-to-str :measures (list-visit_tasks {:vid vid}) ))
  ;             ;(def tasks (list-visit_tasks {:vid vid}) )
  ;             (println "tasks: " tasks)
  ;   
  ;             (conj {:tasks tasks }
  ;                   {:notes (list-visit_notes {:vid vid})} 
  ;                   r ) ) 
  ;       (list-visits-by-pid {:pid (Integer. pid)})    )
  ;   
  ;     ;(->> (list-visits-by-pid {:pid (Integer. pid)})    
  ;     ;     (map  #(conj {:tasks (list-visit_tasks {:vid (:vid %)} )}
  ;     ;                  {:notes (list-visit_notes {:vid (:vid %)} )} 
  ;     ;                  % ) ) )
  ;   >>>>>>> dc52f2bc0a8b6c77ff28888cbbb3099cf741222f
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

; find a specific task
(defn visit-task  
 "get a specific task by id"
 [vtid]
 ;TODO checks
 ; Cannot JSON encode object of class: class org.postgresql.util.PGobject:
 (get-visit-task-by-id {:vtid (Integer. vtid)})
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
     ; bootstrap
     [:link { :rel "stylesheet"
              :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css" 
              :integrity "sha512-dTfge/zgoMYpP7QbHy4gWMEGsbsdZeCXz7irItjcC3sPUFtf0kuFbDz/ixG7ArTxmDjLXDmezHubeNikyKGVyQ==" 
              :crossorigin "anonymous" } ]
     ; boot strap js depends
     [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"}]
     [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js" 
               :integrity "sha512-K1qjQ+NcF2TYO/eI3M6v8EiNYZfA95pQumfvcVrTHtwQVDG+aHRqLi/ETn2uB+1JqwYqVG3LIvdm9lj6imS/pQ==" 
               :crossorigin "anonymous"}]
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
     (include-css "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css")
     ; date picker
     [:style (-> "reagent-forms.css" clojure.java.io/resource slurp) ]
   ]
     ;(include-css "css/pikaday.css")]
    [:body
     [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      (include-js "js/app.js")]]]))

(defn add-visit [pid body]
  (println pid body)
)

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

  (GET "/visit_task/:vtid" [vtid] (json-response (visit-task vtid) ))

  ;; INSERT 
  ; schedule
  (POST "/person/:pid/visit" [pid req ] (json-response (add-visit pid req) ))
  ;(POST "/person/:pid/visit" {params :params} (json-response (visit-add    params) ))
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
  (not-found "Not Found")
)
; remove anti-forgery so we can curl as we please
(def my-site-defaults (assoc-in site-defaults [:security :anti-forgery] false )  )

(def app
  ;(let [handler (wrap-defaults #'routes my-site-defaults)]
  (let [handler (wrap-defaults #'routes (assoc-in site-defaults [:security :anti-forgery] false) )]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
