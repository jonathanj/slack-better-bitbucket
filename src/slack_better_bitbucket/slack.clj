(ns slack-better-bitbucket.slack
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer [generate-string]]
            [slack-better-bitbucket.util :refer [dump]]))


(defn -author
  ([name]
   (-author name nil nil))
  ([name icon]
   (-author name nil icon))
  ([name link icon]
   {:author_name name
    :author_link link
    :author_icon icon}))


(defn -title
  ([text]
   (-title text nil))
  ([text link]
   {:title text
    :title_link link}))


(defn -field
  ([title value]
   (-field title value true))
  ([title value short?]
   {:title title
    :value value
    :short short?}))


(defn build-pretext [{:keys [pretext]}]
  (if (sequential? pretext)
    (clojure.string/join " " pretext)
    pretext))


(defn attachment
  [{:keys [fallback color pretext author title text fields image thumb mrkdwn_in]
    :or {color "#3572b0"
         author []
         title []
         mrkdwn_in ["pretext" "text" "fields"]
         fields []
         fallback (build-pretext payload)}
    :as payload}]
  (conj
   {:fallback fallback
    :color color
    :pretext (build-pretext payload)
    :text text
    :fields (map #(apply -field %)
                 (keep identity fields))
    :mrkdwn_in mrkdwn_in}
   (when-not (empty? author)
     (apply -author author))
   (when-not (empty? title)
     (apply -title title))))

(defn post-message!
  [uri attachments & {:keys [username icon_url debug?]
                      :or {debug? false}}]
  (let [body (generate-string {:attachments attachments
                               :username username
                               :icon_url icon_url})]
    (when debug?
      (dump body "slack"))
    (http/post uri {:body body})))
