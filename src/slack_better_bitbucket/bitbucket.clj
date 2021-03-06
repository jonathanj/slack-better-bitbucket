(ns slack-better-bitbucket.bitbucket
  (import [java.net URI])
  (:require [liberator.core :refer [defresource]]
            [compojure.core :as compojure :refer [ANY]]
            [cheshire.core :refer [parse-string]]
            [slack-better-bitbucket.util :refer [dump]]
            [slack-better-bitbucket.slack :as slack]))


(defn munge-api-href
  "Munge an API href to an HTML href."
  [href]
  (let [uri      (URI/create href)
        segments (clojure.string/split (.getPath uri) #"/")]
    (.toString
     (URI. (.getScheme uri)
           (.getUserInfo uri)
           (clojure.string/replace (.getHost uri) "api." "")
           (.getPort uri)
           (str "/" (clojure.string/join "/" (drop 3 segments)))
           (.getQuery uri)
           (.getFragment uri)))))


(defn web-ref
  "Construct a clickable link to an entity."
  [entity title]
  (let [href (get-in entity [:links :html :href]
                     (munge-api-href (get-in entity [:links :self :href])))]
    (if href
      (format "<%s|%s>" href title)
      title)))


(defn link-for
  ([m key text-key]
   (if (empty? text-key)
     (m key)
     (web-ref (m key) (get-in m (cons key text-key))))))


(defn slice [s n]
  (subs s 0 (min n (count s))))


(defn tag [repo]
  (str "[" (:full_name repo) "]"))


(defn- verb-to-name [verb]
  (clojure.string/replace (name verb) #"[_-]" " "))


(defmulti ascribed (fn [entity & args] (:type entity)))

(defmethod ascribed "user"
  [entity text]
  (str "@" (:username entity) " " text))

(defmethod ascribed "issue"
  [entity text]
  (ascribed (:reporter entity) text))


(defn id-title [entity]
  (format "#%s: %s" (:id entity) (:title entity)))


(defmulti action-for (fn [entity & _] (:type entity)))

(defmethod action-for "pullrequest"
  [pr verb]
  (format "*%s* pull request *%s*"
          (verb-to-name verb)
          (web-ref pr (id-title pr))))


(defmethod action-for "issue"
  ([issue verb]
   (format "*%s* %s *%s*"
           (verb-to-name verb)
           (:kind issue)
           (web-ref issue (id-title issue))))
  ([issue verb comment]
   (format "%s %s *%s*"
           (web-ref comment (verb-to-name verb))
           (:kind issue)
           (web-ref issue (id-title issue)))))


(defmethod action-for "change"
  [change verb commits]
  (let [n (count commits)]
    (format "*%s* %s commit%s to _%s_"
            (verb-to-name verb)
            (str n (when (:truncated change) "+"))
            (if (= n 1) "" "s")
            (link-for change :new [:name]))))


(defn field-for [entity title path]
  (if-let [value (get-in entity path)]
    [title (link-for entity (first path) (rest path))]))


(defn pr-branches [pullrequest]
  (let [src-repo   (get-in pullrequest [:source :repository :full_name])
        src-branch (get-in pullrequest [:source :branch :name])
        dst-repo   (get-in pullrequest [:destination :repository :full_name])
        dst-branch (get-in pullrequest [:destination :branch :name])]
    (if (= src-repo dst-repo)
      (format "%s → %s" src-branch dst-branch)
      (format "%s:%s → %s:%s" src-repo src-branch dst-repo dst-branch))))


(defmulti format-message :event)

(defmethod format-message :default
  [{:keys [event]}]
  [{:fallback "Fallback"
    :pretext (format "Unhandled event: `%s`" event)}])


(defmethod format-message "pullrequest:created"
  [{{:keys [actor repository pullrequest]} :payload}]
  [{:pretext [(tag repository)
              (ascribed actor
                        (action-for pullrequest :created))]
    :fields [["Branches" (pr-branches pullrequest)]]}])


(defmethod format-message "pullrequest:updated"
  [{{:keys [actor repository pullrequest]} :payload}]
  [{:pretext [(tag repository)
              (ascribed actor
                        (action-for pullrequest :updated))]}])


(defmethod format-message "pullrequest:fulfilled"
  [{{:keys [actor repository pullrequest]} :payload}]
  [{:pretext [(tag repository)
              (ascribed actor
                        (action-for pullrequest :merged))]
    :fields [["Branches" (pr-branches pullrequest)]]}])

(defmethod format-message "pullrequest:approved"
  [{{:keys [repository pullrequest approval]} :payload}]
  [{:pretext [(tag repository)
              (ascribed (:user approval)
                        (action-for pullrequest :approved))]}])

(defmethod format-message "pullrequest:unapproved"
  [{{:keys [repository pullrequest approval]} :payload}]
  [{:pretext [(tag repository)
              (ascribed (:user approval)
                        (action-for pullrequest :unapproved))]}])

(defmethod format-message "pullrequest:comment_created"
  [{{:keys [repository pullrequest comment]} :payload}]
  [{:pretext [(tag repository)
              (ascribed (:user comment)
                        (action-for pullrequest :commented_on))]
    :text (get-in comment [:content :raw])}])

(defmethod format-message "issue:created"
  [{{:keys [actor issue comment repository]} :payload}]
  [{:pretext [(tag repository)
              (ascribed actor
                        (action-for issue :created))]
    :fields [(field-for issue "Component" [:component :name])
             (field-for issue "Assignee" [:assignee :username])
             (field-for issue "Milestone" [:milestone :name])
             (field-for issue "Version" [:version :name])
             (field-for issue "Priority" [:priority])]}])

(defmethod format-message "issue:comment_created"
  [{{:keys [actor issue comment repository]} :payload}]
  [{:pretext [(tag repository)
              (ascribed actor
                        (action-for issue :commented_on comment))]}])


(defn issue-changes [changes]
  (for [[k v] changes]
    [(verb-to-name k) (format "%s → %s" (:old v) (:new v))]))


(defmethod format-message "issue:updated"
  [{{:keys [actor repository issue comment changes]} :payload}]
  [{:pretext [(tag repository)
              (ascribed actor
                        (action-for issue :updated comment))]
    :fields (issue-changes changes)}])


(defn format-commit [commit]
  (format "`%s` *@%s* %s"
          (web-ref commit (slice (:hash commit) 8))
          (get-in commit [:author :user :username])
          (:message commit)))


(defn format-commits [commits]
  (clojure.string/join "\n" (map format-commit commits)))


(defn change-attachment [repository actor show-commits? change]
  (let [{:keys [commits]} change]
    (merge
     {:pretext [(tag repository)
                (ascribed actor
                          (action-for (assoc change :type "change")
                                      (if (:forced change)
                                        :force_pushed
                                        :pushed)
                                      commits))]}
     (when show-commits?
       {:text (format-commits commits)}))))


(def whitelist #{"default" "prod" "uat"})


(defn- is-whitelisted-change [whitelist change]
  (not (nil? (whitelist (get-in change [:new  :name])))))


(defmethod format-message "repo:push"
  [{{:keys [actor push repository]} :payload}]
  (map (partial change-attachment repository actor false)
       (filter (partial is-whitelisted-change whitelist) (:changes push))))


(defresource bitbucket-event-resource [post-slack-message! debug?]
  :available-media-types ["application/json"]
  :allowed-methods [:post :get]
  :handle-ok (fn [ctx] (::data ctx "SUP"))
  :post!
  (fn [ctx]
    (let [body        (slurp (get-in ctx [:request :body]))
          event       (get-in ctx [:request :headers "x-event-key"])
          payload     (parse-string body true)
          attachments (map slack/attachment
                           (format-message {:event event :payload payload}))]
      (when debug?
        (println event (dump body "bitbucket")))
      (when attachments
        (post-slack-message!
         attachments
         :username "Better Bitbucket"
         :icon_url "https://slack.global.ssl.fastly.net/fa957/plugins/bitbucket/assets/bot_48.png"
         :debug? debug?))
      {::data "{\"success\": true}"}))
  :handle-created ::data)


(defn routes [post-slack-message!]
  (compojure/routes
   (ANY "/event" {{debug "debug"
                   :or {debug false}} :query-params
                  :as req}
     (bitbucket-event-resource post-slack-message! debug))))
