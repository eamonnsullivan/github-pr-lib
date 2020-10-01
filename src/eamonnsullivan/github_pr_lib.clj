(ns eamonnsullivan.github-pr-lib
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def github-url "https://api.github.com/graphql")
(def ^:dynamic *search-page-size* 10)

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

(def search-for-pr-id-query "query ($owner: String!, $name: String!, $first: Int!, $after: String)  {
    repository(owner:$owner, name:$name) {
      id
      pullRequests(first: $first, after: $after, states:[OPEN]) {
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

(def update-pull-request-mutation "mutation ($pullRequestId: ID!, $title: String, $body: String) {
  updatePullRequest(input: {pullRequestId: $pullRequestId,
  title: $title,
  body: $body}) {
    pullRequest {
      id
      permalink
    }
  }
}")

(def mark-ready-for-review-mutation "mutation ($pullRequestId: ID!) {
  markPullRequestReadyForReview(input: {pullRequestId: $pullRequestId}) {
    pullRequest {
      id
      permalink
    }
  }
}")

(def add-comment-mutation "mutation ($pullRequestId: ID!, $body: String!) {
  addComment(input: {subjectId: $pullRequestId, body: $body}) {
    commentEdge {
      node {
        id
      }
    }
  }
}")

(def close-pull-request-mutation "mutation ($pullRequestId: ID!) {
  closePullRequest(input: {pullRequestId: $pullRequestId}) {
    pullRequest {
      id
      permalink
    }
  }
}")

(def reopen-pull-request-mutation "mutation ($pullRequestId: ID!) {
  reopenPullRequest(input: {pullRequestId: $pullRequestId}) {
    pullRequest {
      id
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

(defn make-graphql-post
  [access-token query variables]
  (let [payload (json/write-str {:query query :variables variables})
        response (http-post github-url payload (request-opts access-token))
        body (json/read-str (response :body) :key-fn keyword)
        errors (:errors body)]
    (if errors
      (throw (ex-info (:message (first errors)) response))
      body)))

(defn parse-repo
  [url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*).*$" url)
        [_ _ owner name] matches]
    (if (and owner name (not-empty owner) (not-empty name))
      {:owner owner :name name}
      nil)))

(defn pull-request-number
  [pull-request-url]
  (let [matches (re-matches #"(https://github.com/)?[^/]*/[^/]*/pull/([0-9]*)" pull-request-url)
        [_ _ number] matches]
    (if (not-empty number)
      (Integer/parseInt number)
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

(defn get-page-of-search-results
  [access-token owner name page-size cursor]
  (let [variables {:owner owner :name name :first page-size :after cursor}
        payload (json/write-str {:query search-for-pr-id-query :variables variables})
        response (http-post github-url payload (request-opts access-token))]
    (json/read-str (response :body) :key-fn keyword)))

(defn get-open-pr-id
  ([access-token pull-request-url]
   (let [repo (parse-repo pull-request-url)
         prnum (pull-request-number pull-request-url)
         owner (:owner repo)
         name (:name repo)]
     (if repo
       (get-open-pr-id access-token owner name prnum)
       nil)))
  ([access-token owner name pull-request-number]
   (let [pull-request-url (string/lower-case (format "https://github.com/%s/%s/pull/%s" owner name pull-request-number))
         page (get-page-of-search-results access-token owner name *search-page-size* nil)]
     (loop [page page
            prs []]
       (let [pageInfo (-> page :data :repository :pullRequests :pageInfo)
             has-next (:hasNextPage pageInfo)
             cursor (:endCursor pageInfo)
             pull-requests (-> page :data :repository :pullRequests :nodes)
             prs (concat prs pull-requests)]
         (if-not has-next
           (:id (first (filter #(= (:url %) pull-request-url) prs)))
           (recur (get-page-of-search-results access-token owner name *search-page-size* cursor)
                  prs)))))))

(defn modify-pull-request
  ([access-token url query]
   (modify-pull-request access-token url query nil))
  ([access-token url query variables]
   (let [pr-id (get-open-pr-id access-token url)]
     (when pr-id
       (let [merged-variables (merge variables {:pullRequestId pr-id})]
         (make-graphql-post access-token query merged-variables))))))


(def create-pr-defaults {:draft true
                         :maintainerCanModify true})

(defn create-pull-request
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
                   :maintainerCanModify maintainerCanModify}]
    (when repo-id
      (let [body (make-graphql-post access-token create-pull-request-mutation variables)]
        (-> body :data :createPullRequest :pullRequest :permalink)))))

(defn update-pull-request
  [access-token pull-request-url updated]
  (let [body (modify-pull-request access-token pull-request-url update-pull-request-mutation updated)]
    (-> body  :data :updatePullRequest :pullRequest :permalink)))

(defn mark-ready-for-review
  [access-token pull-request-url]
  (let [body (modify-pull-request access-token pull-request-url update-pull-request-mutation)]
    (-> body :data :markPullRequestReadyForReview :pullRequest :permalink)))

(defn add-pull-request-comment
  [access-token pull-request-url comment-body]
  (let [body (modify-pull-request access-token pull-request-url add-comment-mutation {:body comment-body})]
    (-> body :data :addComment :commentEdge :node :url)))

(defn close-pull-request
  [access-token pull-request-url]
  (let [body (modify-pull-request access-token pull-request-url close-pull-request-mutation)]
    (-> body :data :closePullRequest :pullRequest :permalink)))

(defn reopen-pull-request
  [access-token pull-request-url]
  (let [body (modify-pull-request access-token pull-request-url reopen-pull-request-mutation)]
    (-> body :data :reopenPullRequest :pullRequest :permalink)))
