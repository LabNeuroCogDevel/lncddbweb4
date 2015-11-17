; copy paste from 
; http://hiim.tv/clojure/2014/05/15/clojure-postgres-json/
(ns web4.jdbcjson
  "Inspired by http://www.niwi.be/2014/04/13/postgresql-json-field-with-clojure-and-jdbc/"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json])
  (:import org.postgresql.util.PGobject))

(defn value-to-json-pgobject [value]
  (doto (PGobject.)
    (.setType "json")
      (.setValue (json/write-str value))))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value))

  clojure.lang.IPersistentVector
  (sql-value [value] (value-to-json-pgobject value)))

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json"  (json/read-str value :key-fn keyword)
        "jsonb" (json/read-str value :key-fn keyword)
        :else value))))
