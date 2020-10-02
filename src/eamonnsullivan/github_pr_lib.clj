(ns eamonnsullivan.github-pr-lib
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def github-url "https://api.github.com/graphql")
(def ^:dynamic *search-page-size* 50)

(def get-repo-id-query "query($owner: String!, $name: String!) {
    repository(owner:$owner, name:$name) {
      id
    }
  }")

(def create-pull-request-mutation "mutation
($title: String!, $body: String, $repositoryId: ID!, $base: String!, $branch: String!, $draft: Boolean!, $maintainerCanModify: Boolean) {
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

(def search-for-open-pr-id-query "query ($owner: String!, $name: String!, $first: Int!, $after: String)  {
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

(def search-for-pr-id-query "query ($owner: String!, $name: String!, $first: Int!, $after: String)  {
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

(def merge-pull-request-mutation "mutation ($pullRequestId: ID!, $title: String, $body: String, $mergeMethod: PullRequestMergeMethod, $authorEmail: String, $expectedHeadRef: GitObjectID ) {
  mergePullRequest(input: {pullRequestId: $pullRequestId,
                           commitHeadline: $title,
                           commitBody: $body,
                           mergeMethod: $mergeMethod,
                           authorEmail: $authorEmail,
                           expectedHeadOid: $expectedHeadRef}) {
    pullRequest {
      id
      permalink
    }
  }
}")

(defn request-opts
  "Add the authorization header to the http request options."
  [access-token]
  {:ssl? true :headers {"Authorization" (str "bearer " access-token)}})

(defn http-post
  "Make a POST request to a url with body payload and request options."
  [url payload opts]
  (client/post url (merge {:content-type :json :body payload} opts)))

(defn make-graphql-post
  "Make a GraphQL request to Github using the provided query/mutation
  and variables. If there are any errors, throw a RuntimeException,
  with the message set to the first error and the rest of the response
  as the cause/additional information."
  [access-token graphql variables]
  (let [payload (json/write-str {:query graphql :variables variables})
        response (http-post github-url payload (request-opts access-token))
        body (json/read-str (response :body) :key-fn keyword)
        errors (:errors body)]
    (if errors
      (throw (ex-info (:message (first errors)) response))
      body)))

(defn parse-repo
  "Parse a repository url (a full url or just the owner/name part) and
  return a map with :owner and :name keys."
  [url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*).*$" url)
        [_ _ owner name] matches]
    (if (and owner name (not-empty owner) (not-empty name))
      {:owner owner :name name}
      nil)))

(defn pull-request-number
  "Get the pull request number from a full or partial URL."
  [pull-request-url]
  (let [matches (re-matches #"(https://github.com/)?[^/]*/[^/]*/pull/([0-9]*)" pull-request-url)
        [_ _ number] matches]
    (if (not-empty number)
      (Integer/parseInt number)
      nil)))

(defn get-repo-id
  "Get the unique ID value for a repository."
  ([access-token url]
   (let [repo (parse-repo url)
         owner (:owner repo)
         name (:name repo)]
     (if repo
       (get-repo-id access-token owner name)
       nil)))
  ([access-token owner repo-name]
   (let [variables {:owner owner :name repo-name}]
     (-> (make-graphql-post access-token get-repo-id-query variables)
         :data
         :repository
         :id))))

(defn get-page-of-search-results
  "Get a page of pull requests, optionally (and by default) filtered by
  those with a status of open."
  ([access-token owner name page-size cursor]
   (get-page-of-search-results access-token owner name page-size cursor true))
  ([access-token owner name page-size cursor open?]
   (let [variables {:owner owner :name name :first page-size :after cursor}
         query (if open? search-for-open-pr-id-query search-for-pr-id-query)]
     (make-graphql-post access-token query variables))))

(defn get-pull-request-id
  "Find the unique ID of a pull request on the repository at the
  provided url. Set must-be-open? to true to filter the pull requests
  to those with a status of open. Returns nil if not found."
  [access-token url must-be-open?]
  (let [repo (parse-repo url)
        prnum (pull-request-number url)
        owner (:owner repo)
        name (:name repo)]
    (let [pull-request-url (string/lower-case (format "https://github.com/%s/%s/pull/%s" owner name prnum))
          page (get-page-of-search-results access-token owner name *search-page-size* nil must-be-open?)]
      (loop [page page
             prs []]
        (let [pageInfo (-> page :data :repository :pullRequests :pageInfo)
              has-next (:hasNextPage pageInfo)
              cursor (:endCursor pageInfo)
              pull-requests (-> page :data :repository :pullRequests :nodes)
              prs (concat prs pull-requests)]
          (if-not has-next
            (:id (first (filter #(= (:url %) pull-request-url) prs)))
            (recur (get-page-of-search-results access-token owner name *search-page-size* cursor must-be-open?)
                   prs)))))))

(defn get-open-pr-id
  "Find the unique ID of an open pull request. Returns nil of none are found."
  ([access-token pull-request-url]
   (get-pull-request-id access-token pull-request-url true)))

(defn modify-pull-request
  "Modify a pull request at the url with the provided mutation."
  ([access-token url mutation]
   (modify-pull-request access-token url mutation nil))
  ([access-token url mutation variables]
   (let [pr-id (or (get-open-pr-id access-token url) (get-pull-request-id access-token url false))]
     (when pr-id
       (let [merged-variables (merge variables {:pullRequestId pr-id})]
         (make-graphql-post access-token mutation merged-variables))))))


(def create-pr-defaults {:draft true
                         :maintainerCanModify true})

(defn create-pull-request
  "Create a pull request on Github repository.

  Arguments:
  * access-token -- the Github access token to use. Must have repo permissions.

  * url -- the URL of the repo (optional). The URL can omit the
  https://github.com/, e.g. owner/repo-name.

  * pull-request -- a map describing the pull
  request. Keys: :title, :base (the base branch), :branch (the branch
  you want to merge) and (if a URL isn't provided) the :owner (or
  organisation) and :name of the repo. Optional keys
  include :draft (default: true), indicating whether the pull request
  is ready for review and :maintainerCanModify (default: true)
  indicating whether the repo owner is allowed to modify the pull
  request."
  ([access-token url pull-request]
   (let [repo (parse-repo url)]
     (if repo
       (create-pull-request access-token (merge pull-request repo))
       (throw (ex-info (format "Unable to find Github repo at %s" url) repo)))))
  ([access-token pull-request]
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
      (-> (make-graphql-post access-token create-pull-request-mutation variables)
          :data
          :createPullRequest
          :pullRequest
          :permalink)))))

(defn update-pull-request
  "Update an existing pull request.

  Argments:
  * access-token -- the Github access token to use. Must have repo permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pulls/1) or
  partial (owner/name/pulls/1) URL of the pull request.

  * updated -- a map describing the update. The keys: :title, :body."
  [access-token pull-request-url updated]
  (-> (modify-pull-request access-token pull-request-url update-pull-request-mutation updated)
      :data
      :updatePullRequest
      :pullRequest
      :permalink))

(defn mark-ready-for-review
  "Mark a pull request as ready for review.
  This effectively just toggles the :draft property of the pull request to false.

  Arguments:
  * access-token -- the Github access token to use. Must
  have repo permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pulls/1) or
  partial (owner/name/pulls/1) URL of the pull request.
  "
  [access-token pull-request-url]
  (-> (modify-pull-request access-token pull-request-url mark-ready-for-review-mutation)
      :data
      :markPullRequestReadyForReview
      :pullRequest
      :permalink))

(defn add-pull-request-comment
  "Add a top-level comment to a pull request.

  Arguments:
  * access-token -- the Github access token to use. Must
  have repo permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pulls/1) or
  partial (owner/name/pulls/1) URL of the pull request.

  * comment-body -- the comment to add.
  "
  [access-token pull-request-url comment-body]
  (-> (modify-pull-request access-token pull-request-url add-comment-mutation {:body comment-body})
      :data
      :addComment
      :commentEdge
      :node
      :url))

(defn close-pull-request
  "Change the status of a pull request to closed.

  Arguments:
  * access-token -- the Github access token to use. Must
  have repo permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pulls/1) or
  partial (owner/name/pulls/1) URL of the pull request.
  "
  [access-token pull-request-url]
  (-> (modify-pull-request access-token pull-request-url close-pull-request-mutation)
      :data
      :closePullRequest
      :pullRequest
      :permalink))

(defn reopen-pull-request
  "Change the status of a pull request to open.

  Arguments:
  * access-token -- the Github access token to use. Must
  have repo permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pulls/1) or
  partial (owner/name/pulls/1) URL of the pull request.
  "
  [access-token pull-request-url]
  (-> (modify-pull-request access-token pull-request-url reopen-pull-request-mutation)
      :data
      :reopenPullRequest
      :pullRequest
      :permalink))

(defn merge-pull-request
  "Merge a pull request.

  Arguments:
  * access-token -- the Github access token to use. Must have repo
  permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pulls/1) or
  partial (owner/name/pulls/1) URL of the pull request.

  * merge-options -- a map with keys that can include :title (the
  headline of the commit), :body (any body description of the
  commit), :mergeMethod (default \"SQUASH\", but can also be
  \"MERGE\" or \"REBASE\"), :authorEmail and :expectedHeadRef. If
  the last is provided, the main branch's head commit must match this
  ID or the merge will be aborted.

  All of these fields are optional."
  [access-token pull-request-url merge-options]
  (-> (modify-pull-request access-token pull-request-url merge-pull-request-mutation merge-options)
      :data
      :mergePullRequest
      :pullRequest
      :permalink))
