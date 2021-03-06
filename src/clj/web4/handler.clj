(ns web4.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [compojure.handler :refer (site)]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css html5]]
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
            ; and on the server
            ; work between types
            [clojure.data.json :as cjson]

            ; get timestamp from string to datetime object
            [clj-time.core :as t]
            [clj-time.coerce :as tc] 
            [clj-time.format :as tf] 

            ; google cal
            ;[google-apps-clj.core :as gcal]
            [google-apps-clj.google-calendar :as gcal]

            ; authetication
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows] 
                             [credentials :as creds]) 
				[hiccup.element :as e]

            ;auth
            [environ.core :refer [env]]
            ;[clj-ldap-auth.ldap :as ldapauth]
            [clj-ldap.client :as ldap]


           ))

;; google calendar
; NOTE: this will expire occasionally!
(def google-ctx (-> "resources/google-creds.edn" slurp clojure.edn/read-string))

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
;  * insert-newvisit<! 
;  * get-idv-visit
;  * update-vt-measure!
(defqueries "sql/visits.sql" {:connection db-spec})


; * insert-note
; * list-person-only-notes
; * insert-person-note!
; * insert-drop-view<!
(defqueries "sql/note.sql" {:connection db-spec})
; * list-study
; * list-tasks
; * list-newest 
; * list-etypes
; * list-drops
(defqueries "sql/study.sql" {:connection db-spec})

; * list-contacts
(defqueries "sql/contact.sql" {:connection db-spec})
;;;;




;;;; AUTHENTICATION
(def ldap-server (ldap/connect {:host "acct.upmchs.net"}))

(def users {"RA" {:username "RA" :password (creds/hash-bcrypt "RA") :role #{::RA}}
            "localadmin" {:username "localadmin" :password (creds/hash-bcrypt "local!123") :roles #{::admin}}})

(defn loginas [req]
 [:html
   [ :p  "You have successfully authenticated as "
       (friend/current-authentication)]
 ]
)


; use users login or ldap
(defn isauth? [in]
 (when (or
   (creds/bcrypt-credential-fn users in)  
   (ldap/bind? ldap-server (str "1UPMC-ACCT\\" (:username in)) (:password in)) 
  )
   {:username (:username in) :role #{::RA}})
)

(defn auth-user [user]
  (println (str "USER:" user))
  {:useranme "RA"}
)

(defn add-ra [m]
  (assoc m :ra (:username (friend/current-authentication)))
)

;;;;

; routing
; handle url  is '/web4/...' or '/...' depending on the host
;(defn h-url [url]
;   (str (if (re-matches #".*arnold.*" (.. java.net.InetAddress getLocalHost getHostName))
;         "/web4"
;         "")
;         url)
;)

;;;;

(defn listsubj 
  "list of all subjects matching subjname"
  [subjname]
  ;(html5 (list-people-by-name db-spec subjname ))
  (println "listsubj for name: " subjname)
  ;(println subjname (list-people-by-name {:name subjname}  )  )
  (list-people-by-name {:name subjname}  )
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
; wrap in data key of map if not already a map
(defn parse-sql-return [r]
 ; eg. class java.lang.Integer for ! (but not <!)
 (println (str "returned from sql: '" (type r)"' " r ))
 ;(if (nil? (re-matches #"^class.*|.*Map.*" (str (type r))))
 (if (nil? (re-matches #".*Map.*" (str (type r))))
   {:data r}
   r
 )
)

(defn sql-add-error 
 "run yesql function 'sqlf' with 'params', return hasmap that has error
  null if no error, error: str exception if there is"
 [sqlf params]
 (println "*sql-add-error: running sql with params: " params)
 (try 
    (conj {:error nil} (doall (parse-sql-return (sqlf params )) ))
    (catch Exception e 
      (let [estr (str e) ]
            ;etrc (apply str (interpose "\n\t" (.printStackTrace (.getNextException e))) )]
        (println (str "ERROR: "  e) )
        (.printStackTrace (.getNextException e))
        (hash-map :error  (str "ERROR: " estr)) )
     )
 )
)


(defn sql-add-error-next [prev-add-err nextfunc doc]
 "chain sql commands: returns prev error or continues to next sql"
 (if (nil? (:error prev-add-err ))
    (merge prev-add-err (sql-add-error nextfunc doc ))
    prev-add-err 
 )
)


(defn pep-search
  "search subjects and get visit summaries"
  [searchmap]
  ; by default we want these things
  (def defsearch {:study "%" :eid "%" :etype "%" :hand "%" :fullname "%" :sex "%" :mincount 0 :minage 0 :maxage 200 :offset 0 :pid 0})
  (def mparms (merge defsearch (select-keys searchmap  (keys defsearch) ))  )

  (println "have:  " mparms) 

  ; earlier invocation
  ;(def mergesearch (merge defsearch (select-keys searchmap  (keys defsearch) ))   )
  ;(def search (reduce (fn [m k] (update-in m [k] #(Integer. %))  ) mergesearch   [:mincount :minage :maxage :offset] ) )
  ;(println search)

  ; make numbers where we should have numbers
  (def search-int 
     (reduce (fn [x y] (update-in x [y]  #(Integer. %) )) mparms [:mincount :minage :maxage :offset :pid] )
  )
  (def search
     (reduce (fn [x y] (update-in x [y]  #(if (nil? %) "%" %) )) search-int [:study :etype :hand :fullname :sex] )
  )

  (println "searching: " search)
  
  ; push hasmap to sql query from yesql function
  ;(def res (list-people-by-name-study-enroll search))
  (def res  (sql-add-error list-people-by-name-study-enroll search))

  ;(println  res ) 
  res
  
  ; old non-json display
  ;(html5 (list-people-by-name db-spec subjname ))
)

; find a specific task
(defn visit-task  
 "get a specific task by id"
 [vtid]
 ; Cannot JSON encode object of class: class org.postgresql.util.PGobject:
 (sql-add-error get-visit-task-by-id {:vtid (Integer. vtid)})
)


(defn person-enroll [params] 
 (sql-add-error person-enroll<! (select-keys params [:pid :etype :id]) )
)
(defn match-or-U [p s]
  (clojure.string/upper-case(or (and (some? s) (re-matches p s)) "U") )
)
; merge params with default settings
(defn select-keys-or-none [needkeys params]
  (def params-nonil (reduce (fn [m k] (update-in m [k] #(or % "none")  )) params needkeys   ) )
  (select-keys params-nonil needkeys) 
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
     :hand (match-or-U #"(?i)^[RLAU]$" (:hand params))})
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

; -- turn objects to json string for given keys 'ks' in map 'doc'
(defn mapkeys-tojson [doc ks]
 (reduce (fn[m k] (assoc m k (-> m k json/generate-string))) doc ks)
)

(defn visit-checkin [params] 
 (let [doc (merge {:ra (:username (friend/current-authentication))} 
                  (select-keys params [:vid :vscore :add-ids :note :tasks]))
      submit (mapkeys-tojson doc [:add-ids :tasks]) ]
 ;
 ;{:vstatus sched, :vscore 5, :add-ids [{:etype LunaID, :id 11469}], :visitno 1, :fname Bart, :age 6.05886379192334, :sex M, :vid 3893, :ids [{:id nil, :etype nil}], :hand R, :studys [{:study MEGEmo, :cohort Control}], :lname Simpson, :note this is not a real visit, :tasks [PVLQuestionnaire fMRIRestingState SpatialWorkingMem ScanSpit], :dob 2010-01-01T05:00:00Z, :pid 1182, :add-task , :notes [TEST Test CogR01 Testing ringreward scan 1], :vtype Scan, :googleuri nil, :vtimestamp 2016-01-23T18:00:00Z}
 (println "checkin visit! " submit)
 (sql-add-error checkin-visit! (clojure.set/rename-keys submit {:add-ids :ids}))
))


; update the meassuers part of the visit_task table
; eg. survey, iq, eyescore
(defn update-vtm [params]
  (sql-add-error update-vt-measure! (select-keys params [:vtid :measures] ) )
)


(def home-page
  (html
   [:html
    [:head
     ; bootstrap
     [:link {:rel "stylesheet" 
         :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css" 
         :integrity "sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7"
         :crossorigin "anonymous"}]

     [:link {:rel "stylesheet" 
         :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css" 
         :integrity "sha384-fLW2N01lMqjakBkx3l/M9EahuwpSfeNvV63J5ezn3uZzapT0u7EYsXMjQV+0En5r" 
         :crossorigin "anonymous"}]
     ;[:link { :rel "stylesheet"
     ;         :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css" 
     ;         :integrity "sha512-dTfge/zgoMYpP7QbHy4gWMEGsbsdZeCXz7irItjcC3sPUFtf0kuFbDz/ixG7ArTxmDjLXDmezHubeNikyKGVyQ==" 
     ;         :crossorigin "anonymous" } ]
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
     (include-css "css/reagent-forms.css")
     ; date picker
     [:style (-> "reagent-forms.css" clojure.java.io/resource slurp) ]
   ]
     ;(include-css "css/pikaday.css")]
    [:body
     [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      (include-js "js/app.js")]]]))

(defn- add-dur [onset dur]
 ;"add duration to a time (as string like 20160101T13000). duration is in hours"
 "add duration to a time. duration is hours (possibly decimal) "
 (println (str dur (class dur)))
 (let [
   ;onset  (tf/parse (tf/formatter "yyyyMMdd'T'HHmmss") strtime)
   durmin (* 60 (mod dur 1))
   durhr  (int (Math/floor dur))
 ]
 (-> onset 
    (t/plus (t/hours durhr) )
    (t/plus (t/minutes durmin) )
 )
))
(defn- unparse-gcal [t]
 "make gcal date"
  (tf/unparse (tf/formatter "yyyy-MM-dd'T'HH:mm:ss") t )
)

(defn add-calendar-visit [vid ra dur]
 "add a visit to the calendar
 need: study vtype vtimestamp sex age fname lname note ra"
  (let [
       v (first (get-visit-by-id {:vid vid}))
      sd (first (:studies v))
      ; prepare cal info
      title (format "%s %s - %.1f yo%s (%s%s)" 
                   (:study sd) (:vtype v)
                   (float (or (:age v) 0))
                   (:sex v) 
                   (-> v :fname (subs 0 1) clojure.string/upper-case)
                   (-> v :lname (subs 0 1) clojure.string/upper-case) )
      curtimestr (tf/unparse 
                      (tf/formatter "yyyy-MM-dd HH:mm") 
                      (t/from-time-zone (t/now) (t/time-zone-for-offset 5)))
      desc (format "%s\nscheduled by %s on %s"
                   (clojure.string/join "\t" (:notes v))
                   ra
                   curtimestr)
  
      t    (tc/from-sql-time (:vtimestamp v))
      gevent (gcal/add-calendar-time-event 
                 google-ctx title desc 
                 ""  ;location
                 (unparse-gcal t )
                 (unparse-gcal (add-dur t (read-string (str dur) ) ))
                 []) ; people invited
 ]

 (println (str "schedualing visit: " v "\n\t" (:vid v) " " (get gevent "id")))
 (update-googleid<! {:vid (:vid v) :googleuri (get gevent "id")})
))

; -----------------

(defn add-person-note [p] 
"add a note, assoc it with a person. done in 2 steps. if note inserts fails, return there"
 (println "add-person-note: " (str p))
 (let [
      note (sql-add-error insert-note-now<! (select-keys p [:pid :ra :note]))
    ]
    ; only run contact-note! if no error in note
    ;(if (nil? (:error note))
    ;  (merge note (sql-add-error contact-note! {:cid (:cid p) :nid (:nid note)}))
    ;  note
    ;)
    (sql-add-error-next note insert-person-note! {:pid (:pid p) :nid (:nid note)})
))

; ------------------
(defn add-contact [p]
 "add contact, coming autheticated from auth-post"
 (sql-add-error insert-contact! (select-keys p [:pid :ctype :cvalue :who :relation]))
)

(defn contact-note [p] 
"add a note, assoc it with a contact. done in 2 steps. if note inserts fails, return there"
 (println "contact-note: " (str p))
 (let [
      note (sql-add-error insert-note-now<! (select-keys p [:pid :ra :note]))
    ]
    ; only run contact-note! if no error in note
    ;(if (nil? (:error note))
    ;  (merge note (sql-add-error contact-note! {:cid (:cid p) :nid (:nid note)}))
    ;  note
    ;)
    (sql-add-error-next note contact-note! {:cid (:cid p) :nid (:nid note)})
))
(defn contact-ban [p] 
"add a note, assoc it with a contact. done in 2 steps. if note inserts fails, return there"
 (let [
      note  (sql-add-error           insert-note-now<! (select-keys p [:pid :ra :note]))
      cn    (sql-add-error-next note contact-note!     {:cid (:cid p) :nid (:nid note)})
     ]
     (sql-add-error-next cn contact-nogood! {:cid (:cid p)})
))

; ------------------


(defn add-summary-visit [params]
" add visit, visit action, visit note, visit study with sql trigger on view.
  need pid study vtype vtimestamp visitno note study cohort,ra and dur.
  N.B. ra likely added by auth-post"
(let [
   v (sql-add-error  
        insert-visit-summary<!
        (select-keys params [:pid :vtype :vtimestamp :visitno :ra :note :study :cohort])
     )
   ]

   ; add to google cal when we've had no error inserting visit
   (when (= (:error v) nil)
     (println "running: (add-calendar-visit " (:vid v) (:ra params) (:dur params) ")" )
     (let [ gevent (add-calendar-visit (:vid v) (:ra params) (:dur params) ) ] 
      (assoc v :googleuri (gevent :googleuri))
   ))

   v
))

(defn get-test-age [p]
   "testing why select insert does not add correct age"
   (test-age (select-keys p [:pid :vtimestamp] ))
)

(defn noshow-visit [p]
 (println p)

 (insert-visitaction-now! (merge {:action "noshow"} (select-keys p [:vid :ra]) ))
 (def nid (:nid (insert-note-now<! (select-keys p [:pid :ra :note] )) ))
 (insert-visitnote<! {:vid (:vid p) :nid nid})

)
(defn cancel-visit [p]
 (println (str "cancel: " p))
 (let [
       v (first (get-visit-by-id (select-keys p [:vid])))
       gu (:googleurl v)
 ]
  (when (not(nil? gu))
   (gcal/delete-calendar-event google-ctx gu)
  )
  (sql-add-error cancel-visit!  (select-keys p [:vid]))
))

(defn resched-visit [p]
 (println (str "TODO: RESCHEDULE" p))
)




;; return json
(defn json-response [data & [status]]
    {:status  (or status 200)
    :headers {"Content-Type" "application/hal+json; charset=utf-8"}
    :body    (json/generate-string data)})

(defn json-slurp [body & params]
 (def bj (json/parse-string (slurp body) true)  )
 (if params
  (merge bj (first params ))
  bj
 )

)

(defn auth-post [url func]
  "post url: slurp all params and exec func in a post and make sure we're authorized"
  (POST url 
        {body :body params :params }  
        (-> (json-slurp body params) 
             add-ra
             func
             json-response 
             friend/authenticated) )
)

(def devcard-page 
 (html
   [:html
    [:body
     [:div#devcards ]
     (include-js "js/app.js")
    ]
   ]
 )
)

(defroutes routes
  (GET "/" [] (friend/authenticated home-page ))
  (GET "/devcard" [] devcard-page )
  ;(GET "/" [] home-page )

  (GET "/whoami" req
   (friend/authenticated 
    (str "You have successfully authenticated as "
         (:username (friend/current-authentication))
         "\n you are authorised? " )))
  
  ;; enroll newest
  (GET "/newest/enroll/:etype" [etype] 
        (json-response(sql-add-error list-newest {:etype etype})))
  ;;;;TODO: nest?

  ;;; people
  ; search
  ;http://0.0.0.0:3000/people/name?n=william
  (GET  "/people/name" [n] (json-response (listsubj n) ))
  (GET  "/people" {params :params} (json-response  (pep-search params) ))

  ; add
  (POST "/person" {body :body} (json-response  (pep-add   (json-slurp body) ) ))
  ; enroll 
  (POST "/person/:pid/enroll" {params :params}  (json-response (person-enroll  params) ))
  ; edit
  (POST "/person/:pid/edit" {params :params} (json-response (person-edit  params) ))

  ;; lists for autocompleting
  ;;;; legacy -- use study as prefix
  (GET "/study/studies"   [] (json-response (map :study (list-studies)) ))
  (GET "/study/cohorts"   [] (json-response (map :cohort (list-cohorts)) ))
  (GET "/study/vtypes"    [] (json-response (map :vtype (list-visittypes)) ))
  (GET "/study/tasks"     [] (json-response             (list-tasks) ))
  (GET "/study/etypes"    [] (json-response             (list-etypes) ))


  ; new -- use list as prefix
  (GET "/list/drops"     [] (json-response             (list-drops)  ))
  (GET "/list/studies"   [] (json-response (map :study (list-studies)) ))
  (GET "/list/cohorts"   [] (json-response (map :cohort (list-cohorts)) ))
  (GET "/list/vtypes"    [] (json-response (map :vtype (list-visittypes)) ))
  (GET "/list/tasks"     [] (json-response             (list-tasks) ))
  (GET "/list/etypes"    [] (json-response             (list-etypes) ))

  ;; NOTES
  ;(GET "/person/:pid/notes" [pid] (json-response (sql-add-error list-person-only-notes  {:pid pid}) ))
  (GET "/person/:pid/notes" [pid] 
      (->> {:pid pid} (sql-add-error list-person-only-notes) json-response) )

  (auth-post "/person/:pid/note" add-person-note)

  ;; DROP
  ; need pid vid dropcode ra and note, pid or vid can be null but not both
  (auth-post "/drop" 
    (fn[p] (sql-add-error insert-drop-view<! (merge {:pid nil :vid nil} (select-keys p [:pid :vid :dropcode :ra :note] )))
  ))

  ;; CONTACTS
  (GET "/person/:pid/contacts" [pid] 
      (->> {:pid pid} (sql-add-error list-contacts) json-response) )
  ; edit
  (auth-post "/person/:pid/addcontact" add-contact )

  (auth-post "/contact/:cid/ban"  contact-ban )
  (auth-post "/contact/:cid/note" contact-note )

  ;; VISITS
  (GET "/person/:pid/visits" [pid] (json-response (visit-search pid) ))


  (GET "/visit_task/:vtid"  [vtid] (json-response (visit-task vtid) ))
  (GET "/visit/:vid"  [vid] (json-response (sql-add-error get-idv-visit {:vid vid}) ))
  (GET "/test/:pid/:vtimestamp"  {params :params} (json-response (get-test-age params) ))

  ;; INSERT/POST
  (auth-post "/person/:pid/visit" add-summary-visit)

  ; check in  -- select tasks, score visit
  (auth-post "/visit/:vid/checkin" visit-checkin)

  ; add files
                                  
  ; EDIT
  (auth-post "/visit/:vid/noshow"  noshow-visit )
  (auth-post "/visit/:vid/cancel"  cancel-visit )
  (auth-post "/visit/:vid/resched" resched-visit )

  ;; w/o auth-post

  ; schedule
  ;(POST "/person/:pid/visit_old" {body :body params :params }  (json-response (add-visit  (json-slurp body params))))
  ;(POST "/visit/:vid/resched" 
  ;      {body :body params :params }  
  ;      (-> (json-slurp body params) 
  ;           resched-visit 
  ;           json-response 
  ;           friend/authenticated) )
  ;
  ;(POST "/visit/:vid/noshow" {body :body params :params }  (json-response (noshow-visit  (json-slurp body params))))
  ;(POST "/visit/:vid/cancel" {body :body params :params }  (json-response (cancel-visit  (json-slurp body params))))

  ;; visit task
  ; edit
  (auth-post "/task/:vtid" update-vtm)
  ;(POST "/task/:vtid" 
  ;      {body :body params :params }  (json-response (update-vtm (json-slurp body params))))
  ;(GET "task/:vtid" (println "GET TASK NOT DEFINED"))
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
  (let [
      friendset {:allow-anon? true
                     :unauthenticated-handler #(workflows/http-basic-deny "LNCDDB" %)
                     :workflows [(workflows/http-basic
                                  :credential-fn #(isauth? %)
                                  :realm "LNCDDB")]}
      settings (assoc-in site-defaults [:security :anti-forgery] false) 
      ;friended (friend/authenticated  #'routes friendset)
      ;handler (wrap-defaults friended settings )
      ;handler (wrap-defaults #'routes settings )
      handler (-> #'routes 
                  (friend/authenticate friendset)
                  (wrap-defaults settings))
  ] 
    (if (env :dev) 
      (-> handler wrap-exceptions wrap-reload)
      handler)
))
