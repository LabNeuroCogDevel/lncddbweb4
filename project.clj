; lein new reagent web4

(defproject web4 "0.1.0-SNAPSHOT"

 ; want to be able to use figwheel from synergy-attached computer
 ;:figwheel {:http-server-root "public"
 ;           :server-port 3449
 ;           :server-ip "10.145.65.240"

 ;           :nrepl-port 7002
 ;           :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
 ;           :css-dirs ["resources/public/css"]
 ;           :ring-handler web4.handler/app}

  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring-server "0.4.0"]
                 [reagent "0.5.1"]
                 [reagent-utils "0.1.7"]
                 [reagent-forms "0.5.13"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [prone "1.0.1"]
                 [compojure "1.4.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.2"]
                 [org.clojure/clojurescript "1.7.228" ] ; :scope "provided"]
                 [secretary "1.2.3"]
                 
                 
                 ; get data
                 [org.clojure/core.async "0.2.374"]
                 [cljs-http "0.1.39"]
                 [cljs-ajax "0.5.3"]
                 [cheshire  "5.5.0"]
                
                 ; deal with database
                 [yesql "0.5.2"]
                 [mysql/mysql-connector-java "5.1.38"]
                 [org.postgresql/postgresql "9.4.1207"]

                 ; authentication
                 [com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache] ]

                 ; pretty dates in js
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 ; and on the server
                 [clj-time "0.11.0"]
                 ; date picker
                 [cljs-pikaday "0.1.2"]
                 ; also include css: https://raw.githubusercontent.com/dbushell/Pikaday/master/css/pikaday.css

                 [reagent-forms "0.5.13"]

                 ; pretty colors
                 [rm-hull/inkspot "0.0.1-SNAPSHOT"]

                 ;debug html printing
                 [json-html "0.3.8"]
                
                 ; google cal -- THIS IS LOCAL VERSION WITH DELETE
                 [google-apps-clj "0.3.3.1"]
                 ; not in clojars
                 ;[owainlewis/gcal "0.1.0-SNAPSHOT"]

                 ;ldap auth
                 [clj-ldap-auth "0.1.1"]
                 [org.clojars.pntblnk/clj-ldap "0.0.9"]

                 ; debuging/testing/live
                 [devcards "0.2.1-6"]
                ]

  :plugins [[lein-environ "1.0.1"]
            ;[lein-asset-minifier "0.2.4"]
            ; check for new fun things
            [lein-ancient "0.6.7" :exclusions [org.clojure/tools.reader org.clojure/clojure] ]
            ]

  :ring {:handler web4.handler/app
         :uberwar-name "web4.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "web4.jar"

  :main web4.server

  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc"]

  ;:minify-assets
  ;{:assets
  ;  {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild {:builds
                     #_{:id "devcards"
                        :source-paths ["src"]
                        :figwheel { :devcards true } ;; <- note this
                        :compiler { :asset-path "js/out"
                                    :output-to  "resources/public/js/devcards.js"
                                    :output-dir "resources/public/js/compiled/devcards_out"
                                    :source-map-timestamp true }}

  
                      {:app {:source-paths ["src/cljs" "src/cljc"]
                             :figwheel { :devcards true }
                             :devcards true
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :asset-path   "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :profiles {:dev {:repl-options {:init-ns web4.repl}

                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.4.0"]
                                  [lein-figwheel "0.5.0-6"]
                                  [ring/ring-jetty-adapter "1.4.0"]

                                  ; issue with stringreader
                                  ; https://github.com/clojure-emacs/refactor-nrepl/issues/53
                                  [org.clojure/tools.nrepl "0.2.12" ]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [pjstadig/humane-test-output "0.7.1"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [
                             [refactor-nrepl "1.1.0"]
                             [cider/cider-nrepl "0.10.2" :exclusions [org.clojure/clojure] ]
                             [lein-figwheel "0.5.0-6"]
                             [lein-ring "0.9.7"]
                             [lein-cljsbuild "1.1.1"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              ;:server-ip "10.145.65.240"

                              :nrepl-port 7002
                              :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
                              :css-dirs ["resources/public/css"]
                              :ring-handler web4.handler/app}

                   :env {:dev true }

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:main "web4.dev"
                                                         :source-map true}}
                                       ;:figwheel {:websocket-host "10.145.65.240"}
                                       :figwheel {:devcarsd true}
                                       }
                                        
}}

             :uberjar {:hooks [leiningen.cljsbuild ];minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                             {:source-paths ["env/prod/cljs"]
                                              :compiler
                                              {:optimizations :advanced
                                               :pretty-print false}}}}}})
