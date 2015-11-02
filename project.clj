(defproject slack-better-bitbucket "0.1.0-SNAPSHOT"
  :description "Slack integration for Bitbucket that is actually useful."
  :url "http://github.com/jonathanj/slack-better-bitbucket"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler slack-better-bitbucket.core/handler}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [liberator "0.13"]
                 [compojure "1.4.0"]
                 [cheshire "5.5.0"]
                 [clj-time "0.11.0"]
                 [http-kit "2.1.19"]
                 [ring-server "0.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-defaults "0.1.5"]]
  :main ^:skip-aot slack-better-bitbucket.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
