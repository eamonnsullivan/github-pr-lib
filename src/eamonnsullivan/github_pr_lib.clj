(ns eamonnsullivan.github-pr-lib
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def github-url "https://api.github.com/graphql")

(def get-repo-id-query "query($owner: String!, $name: String!) {
    repository(owner:$owner, name:$name) {
      id
    }
  }")

(def create-pull-request-query "mutation
($title: String!, $body: String!, $repositoryId: ID!, $baseBranch: String!, $mergingBranch: String!, $draft: Boolean!) {
  createPullRequest(input: {
    title: $title,
    body: $body,
    repositoryId: $repositoryId,
    baseRefName: $baseBranch,
    headRefName: $mergingBranch,
    draft: $draft
  }) {
    pullRequest {
      permalink
    }
  }
}")

(defn request-opts
  [access-token]
  {:ssl? true :headers {"Authorization" (str "bearer " access-token)}})

(defn http-post
  [url payload opts]
  (client/post url (merge {:content-type :json :body payload} opts)))

(defn get-repo-id
  [access-token owner repo-name]
  (let [variables {:owner owner :name repo-name}
        payload (json/write-str {:query get-repo-id-query :variables variables})
        response (http-post github-url payload (request-opts access-token))]
    (-> (json/read-str (response :body) :key-fn keyword) :data :repository :id)))
