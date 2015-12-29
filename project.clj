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
                 [reagent-utils "0.1.5"]
                 [reagent-forms "0.5.13"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [prone "0.8.2"]
                 [compojure "1.4.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/clojurescript "1.7.122" :scope "provided"]
                 [secretary "1.2.3"]
                 
                 ; handle authentication
                 [com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache] ]
                 
                 
                 ; get data
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [cljs-http "0.1.37"]
                 [cljs-ajax "0.5.0"]
                 [cheshire  "5.5.0"]
                
                 ; deal with database
                 [yesql "0.5.1"]
                 [mysql/mysql-connector-java "5.1.37"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]

                 ; authentication
                 [com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache] ]

                 ; pretty dates in js
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 ; date picker
                 [cljs-pikaday "0.1.2"]
                 ; also include css: https://raw.githubusercontent.com/dbushell/Pikaday/master/css/pikaday.css

                 [reagent-forms "0.5.13"]

                 ; pretty colors
                 [rm-hull/inkspot "0.0.1-SNAPSHOT"]

                 ;debug html printing
                 [json-html "0.3.6"]
                
                 ; google cal
                 [google-apps-clj "0.2.1"]
                ]

  :plugins [[lein-environ "1.0.1"]
            [lein-asset-minifier "0.2.2"]
            ; check for new fun things
            [lein-ancient "0.6.7"]
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

  :minify-assets
  {:assets
    {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "src/cljc"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :asset-path   "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :profiles {:dev {:repl-options {:init-ns web4.repl}

                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.4.0"]
                                  [lein-figwheel "0.4.0"]
                                  [org.clojure/tools.nrepl "0.2.11"]
                                  [com.cemerick/piggieback "0.1.5"]
                                  [pjstadig/humane-test-output "0.7.0"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.4.0"]
                             [lein-cljsbuild "1.1.0"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              ;:server-ip "10.145.65.240"

                              :nrepl-port 7002
                              :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
                              :css-dirs ["resources/public/css"]
                              :ring-handler web4.handler/app}

                   :env {:dev true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:main "web4.dev"
                                                         :source-map true}}
                                       ;:figwheel {:websocket-host "10.145.65.240"}
                                       }
                                        
}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                             {:source-paths ["env/prod/cljs"]
                                              :compiler
                                              {:optimizations :advanced
                                               :pretty-print false}}}}}})
