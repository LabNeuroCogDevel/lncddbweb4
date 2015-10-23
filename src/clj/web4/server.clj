(ns web4.server
  (:require [web4.handler :refer [app]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            
            
  )
  (:gen-class))

;; http://blog.00null.net/clojure-yesql-and-postgesql-arrays/

;; below does not work with yesql
;https://gist.github.com/MrHus/5370481
; ; deal with postgresql arrays
; [cheshire.generate :refer [add-encoder encode-str remove-encoder]]
; (add-encoder org.postgresql.jdbc4.Jdbc4Array
;  (fn [array jsonGenerator]
;   (let [sequence (seq (.getArray array))]
;    (.writeStartArray jsonGenerator)
;    (doseq [i sequence]
;     (.writeString jsonGenerator i))
;    (.writeEndArray jsonGenerator))))

 (defn -main [& args]
   (let [port (Integer/parseInt (or (env :port) "3000"))]
     (run-jetty app {:port port :join? false})))
