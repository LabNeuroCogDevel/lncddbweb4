(ns web4.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env] ]
            
            
            ;;
            [yesql.core :refer [defqueries]]
            ; send json out
            [cheshire.core :as json]
            [web4.jdbcjson]
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
  (println subjname (list-people-by-name {:name subjname}  )  )
  (list-people-by-name {:name subjname}  )
)

 
(defn pep-search
  "search subjects and get visit summaries"
  [searchmap]
  (println searchmap)
  (def defsearch {:study "%" :etype "%" :hand "%" :fullname "%" :sex "%" :mincount 0 :minage 0 :maxage 200})
  (def search 
   (update-in 
      (merge defsearch (select-keys searchmap  (keys defsearch) ))  
      [:mincount :minage :maxage] 
      int ))

  (println search)
  (def res (list-people-by-name-study-enroll search))

  ;(html5 (list-people-by-name db-spec subjname ))
  (println  res ) 
  res
)

; add tasks to visits
(defn task-lookup [v]
  ; all notes
  (def nt (list-notes-by-vid {:vid (:vid v)}))
  ; all tasks
  (def ts (map #(:task %) (list-tasks-by-vid {:vid (:vid v)})))

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
) 


; find a specific task
(defn visit-task  
 "get a specific task by id"
 [vtid]
 ;TODO checks
 ; Cannot JSON encode object of class: class org.postgresql.util.PGobject:
 (get-visit-task-by-id {:vtid (Integer. vtid)})
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
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]]
     (include-js "js/app.js")]]))

;; return json
(defn json-response [data & [status]]
    {:status  (or status 200)
    :headers {"Content-Type" "application/hal+json; charset=utf-8"}
    :body    (json/generate-string data)})

(defroutes routes
  (GET "/" [] home-page)
  ;http://0.0.0.0:3000/lists?n=william
  (GET "/lists" [n] (json-response (listsubj n) ))
  ;
  (GET "/people" {params :params} (json-response (pep-search params) ))
  (GET "/person/:pid/visits" [pid] (json-response (visit-search pid) ))

  (GET "/visit_task/:vtid" [vtid] (json-response (visit-task vtid) ))
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
