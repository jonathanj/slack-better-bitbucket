(ns slack-better-bitbucket.util
  (:require [clj-time.core :as time]
            [clj-time.coerce :as tc]))


(defn dump [content prefix]
  (let [timestamp (tc/to-long (time/now))
        filename (format "%s-%s.json" prefix timestamp)]
    (with-open [f (clojure.java.io/writer filename)]
      (spit f content))
    filename))
