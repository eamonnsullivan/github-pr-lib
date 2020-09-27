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
($title: String!, $body: String!, $repositoryId: ID!, $base: String!, $branch: String!, $draft: Boolean!, $maintainerCanModify: Boolean) {
  createPullRequest(input: {
    title: $title,
    body: $body,
    repositoryId: $repositoryId,
    baseRefName: $base,
    headRefName: $branch,
    draft: $draft,
    maintainerCanModify: $maintainerCanModify
  }) {
    pullRequest {
      id
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

(defn parse-repo
  [url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*).*$" url)
        [_ _ owner name] matches]
    (if (and owner name (not-empty owner) (not-empty name))
      {:owner owner :name name}
      nil)))

(defn get-repo-id
  ([access-token url]
   (let [repo (parse-repo url)
         owner (:owner repo)
         name (:name repo)]
     (if repo
       (get-repo-id access-token owner name)
       nil)))
  ([access-token owner repo-name]
  (let [variables {:owner owner :name repo-name}
        payload (json/write-str {:query get-repo-id-query :variables variables})
        response (http-post github-url payload (request-opts access-token))
        body (json/read-str (response :body) :key-fn keyword)
        errors (:errors body)]
    (if errors
      (throw (ex-info (:message (first errors)) response))
      (-> body :data :repository :id)))))

(def create-pr-defaults {:draft false
                         :maintainerCanModify true})

(defn createpr
  [access-token pull-request]
  (let [{owner :owner
         repo-name :name
         title :title
         body :body
         base-branch :base
         merging-branch :branch
         draft :draft
         maintainerCanModify :maintainerCanModify} (merge create-pr-defaults pull-request)
        repo-id (get-repo-id access-token owner repo-name)
        variables {:repositoryId repo-id
                   :title title
                   :body body
                   :base base-branch
                   :branch merging-branch
                   :draft draft
                   :maintainerCanModify maintainerCanModify}
        payload (json/write-str {:query create-pull-request-mutation :variables variables})]
    (when repo-id
      (let [response (http-post github-url payload (request-opts access-token))
            body (json/read-str (response :body) :key-fn keyword)
            errors (:errors body)]
        (if errors
          (throw (ex-info (:message (first errors)) response))
          (-> (json/read-str (response :body) :key-fn keyword) :data :createPullRequest :pullRequest :id))))))
