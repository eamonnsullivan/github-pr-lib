(ns eamonnsullivan.github-pr-lib
  (:require [clj-http.client :as client]
            [clojure-string :as string]
            [clojure.data.json :as json]))

(def github-url "https://api.github.com/graphql")

(defn request-opts
  [access-token]
  {:ssl? true :headers {"Authorization" (str "bearer " access-token)}})

(defn http-post
  [url payload opts]
  (client/post url (merge {:content-type :json :body payload} opts)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
