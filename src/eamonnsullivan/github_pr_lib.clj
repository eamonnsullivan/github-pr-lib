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

(def create-pull-request-mutation "mutation
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

(def search-for-pr-id-query "query FindPullRequests ($owner: String!, $name: String!, $first: Int!, $after: String)  {
    repository(owner:$owner, name:$name) {
      id
      pullRequests(first: $first, after: $after) {
        nodes {
          id
          url
        }
        pageInfo {
      	  hasNextPage
      	  endCursor
        }
      }
    }
  }
")

(defn request-opts
  [access-token]
  {:ssl? true :headers {"Authorization" (str "bearer " access-token)}})

(defn http-post
  [url payload opts]
  (client/post url (merge {:content-type :json :body payload} opts)))

(defn get-owner-and-name
  [url]

  {:owner "someone" :name "something"})

(defn get-repo-id
  [access-token owner repo-name]
  (let [variables {:owner owner :name repo-name}
        payload (json/write-str {:query get-repo-id-query :variables variables})
        response (http-post github-url payload (request-opts access-token))
        body (json/read-str (response :body) :key-fn keyword)
        errors (:errors body)]
    (if errors
      (throw (ex-info (:message (first errors)) response))
      (-> body :data :repository :id))))

(defn createpr
  [access-token owner repo-name title body base-branch merging-branch draft]
  (let [repo-id (get-repo-id access-token owner repo-name)
        variables {:title title :body body :baseBranch base-branch :mergingBranch merging-branch :draft draft}
        payload (json/write-str {:query create-pull-request-mutation :variables variables})]
    (if repo-id
      (let [response (http-post github-url payload (request-opts access-token))
            body (json/read-str (response :body) :key-fn keyword)
            errors (:errors body)]
        (if errors
          (throw (ex-info (:message (first errors)) response))
          (-> (json/read-str (response :body) :key-fn keyword) :data :createPullRequest :pullRequest :id)))
      nil)))
