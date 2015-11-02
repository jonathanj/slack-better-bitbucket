(ns slack-better-bitbucket.core
  (:use ring.adapter.jetty)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [compojure.core :refer [context routes]]
            [ring.server.standalone :refer [serve]]
            [ring.middleware.defaults :refer [wrap-defaults secure-api-defaults]]
            [slack-better-bitbucket.slack :as slack]
            [slack-better-bitbucket.bitbucket :as bitbucket])
  (:gen-class))


(defn app [slack-incoming-uri]
  (routes
    (context "/bitbucket" []
      (bitbucket/routes
       (partial slack/post-message! slack-incoming-uri)))))

;; Possibly use (delay) here and define it after start-up like the ring init
;; function.
(defn handler [slack-incoming-uri]
  (-> (app slack-incoming-uri)
      (wrap-defaults secure-api-defaults)))

(def cli-options
  [[nil  "--slack-incoming-uri URI" "URI for Slack incoming webhook"]
   ["-p" "--port PORT" "Port number"
    :default 8880
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   [nil  "--dev" "Development mode"]
   [nil  "--keystore PATH" "Keystore to use for SSL certificates"
    :default "keystore.jks"]
   [nil  "--keystore-password PASSWORD" "Keystore password"]
   ["-h" "--help"]])

(defn usage [summary]
  (->> ["Usage: slack-better-bitbucket [options]"
        ""
        "Options:"
        summary]
       (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "Errors:\n\n" (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; XXX: Enormous hack to remove all non-SSL connectors, one day there will be a
;; ring-jetty-adapter release with the :http? option.
(defn- remove-non-ssl-connectors [server]
  (doseq [c (.getConnectors server)]
    (println c)
    (when-not (or (nil? c))
      (.removeConnector server c)))
  server)

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        {:keys [port slack-incoming-uri keystore keystore-password dev]
         :or   {post 8880}} options]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))

    (serve (handler slack-incoming-uri)
           {:configurator remove-non-ssl-connectors
            :open-browser? false
            :auto-reload? dev
            :stacktraces? dev
            :http? false
            :ssl-port port
            :keystore keystore
            :key-password keystore-password
            :join? false})))
