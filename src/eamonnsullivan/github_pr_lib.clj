(ns eamonnsullivan.github-pr-lib
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def github-url "https://api.github.com/graphql")
(def ^:dynamic *search-page-size* 50)

(def get-repo-id-query (slurp "./src/eamonnsullivan/get-repo-id-query.graphql"))
(def create-pull-request-mutation (slurp "./src/eamonnsullivan/create-pull-request-mutation.graphql"))
(def search-for-pr-id-query (slurp "./src/eamonnsullivan/search-for-pr-id-query.graphql"))
(def update-pull-request-mutation (slurp "./src/eamonnsullivan/update-pull-request-mutation.graphql"))
(def mark-ready-for-review-mutation (slurp "./src/eamonnsullivan/mark-ready-for-review-mutation.graphql"))
(def add-comment-mutation (slurp "./src/eamonnsullivan/add-comment-mutation.graphql"))
(def edit-comment-mutation (slurp "./src/eamonnsullivan/edit-comment-mutation.graphql"))
(def close-pull-request-mutation (slurp "./src/eamonnsullivan/close-pull-request-mutation.graphql"))
(def reopen-pull-request-mutation (slurp "./src/eamonnsullivan/reopen-pull-request-mutation.graphql"))
(def merge-pull-request-mutation (slurp "./src/eamonnsullivan/merge-pull-request-mutation.graphql"))
(def pull-request-query (slurp "./src/eamonnsullivan/pull-request-query.graphql"))
(def search-for-issue-comment-id (slurp "./src/eamonnsullivan/search-for-issue-comment-id-query.graphql"))

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

(defn parse-comment-url
  "Get the full comment url and pull request url from an issue comment URL."
  [comment-url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*)/pull/([0-9]*)#(issuecomment-[0-9]*)" comment-url)
        [_ _ owner name number comment] matches]
    (if (and (not-empty owner)
             (not-empty name)
             (not-empty number)
             (not-empty comment))
      {:pullRequestUrl (format "https://github.com/%s/%s/pull/%s" owner name number)
       :issueComment (format "#%s" comment)}
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

(defn get-page-of-pull-requests
  "Get a page of pull requests, optionally (and by default) filtered by
  those with a status of open."
  ([access-token owner name page-size cursor]
   (get-page-of-pull-requests access-token owner name page-size cursor true))
  ([access-token owner name page-size cursor open?]
   (let [variables {:owner owner :name name :first page-size :after cursor}]
     (if open?
       (make-graphql-post access-token search-for-pr-id-query (merge {:states ["OPEN"]} variables))
       (make-graphql-post access-token search-for-pr-id-query (merge {:states ["OPEN" "CLOSED" "MERGED"]} variables))))))

(defn get-pull-request-id
  "Find the unique ID of a pull request on the repository at the
  provided url. Set must-be-open? to true to filter the pull requests
  to those with a status of open. Returns nil if not found."
  [access-token url must-be-open?]
  (let [repo (parse-repo url)
        prnum (pull-request-number url)
        owner (:owner repo)
        name (:name repo)
        pull-request-url (string/lower-case (format "https://github.com/%s/%s/pull/%s" owner name prnum))
        page (get-page-of-pull-requests access-token owner name *search-page-size* nil must-be-open?)]
    (loop [page page
           prs []]
      (let [pageInfo (-> page :data :repository :pullRequests :pageInfo)
            has-next (:hasNextPage pageInfo)
            cursor (:endCursor pageInfo)
            pull-requests (-> page :data :repository :pullRequests :nodes)
            prs (concat prs pull-requests)]
        (if-not has-next
          (:id (first (filter #(= (:url %) pull-request-url) prs)))
          (recur (get-page-of-pull-requests access-token owner name *search-page-size* cursor must-be-open?)
                 prs))))))

(defn get-page-of-issue-comments
  "Get a page of issues comments on a particular pull request"
  [access-token pull-request-id page-size cursor]
  (let [variables {:pullRequestId pull-request-id :first page-size :after cursor}]
    (make-graphql-post access-token search-for-issue-comment-id variables)))

(defn get-issue-comment-id
  "Find the unique ID of an issue comment on a pull request. Returns nil if not found."
  [access-token comment-url]
  (let [prurl (:pullRequestUrl (parse-comment-url comment-url))]
    (if prurl
      (let [prnum (get-pull-request-id access-token prurl false)
            page (get-page-of-issue-comments access-token prnum *search-page-size* nil)]
        (loop [page page
               comments []]
          (let [pageInfo (-> page :data :node :comments :pageInfo)
                has-next (:hasNextPage pageInfo)
                cursor (:endCursor pageInfo)
                nodes (-> page :data :node :comments :nodes)
                comments (concat comments nodes)]
            (println "EAMONN DEBUG: pageInfo:"  pageInfo)
            (if-not has-next
              (:id (first (filter #(= (:url %) comment-url) comments)))
              (recur (get-page-of-issue-comments access-token prnum *search-page-size* cursor)
                     comments)))))
      nil)))

(defn get-open-pr-id
  "Find the unique ID of an open pull request. Returns nil of none are found."
  ([access-token pull-request-url]
   (get-pull-request-id access-token pull-request-url true)))

(defn get-pull-request-info
  "Find some info about a pull request."
  [access-token pull-request-url]
  (let [pr-id (or (get-open-pr-id access-token pull-request-url)
                  (get-pull-request-id access-token pull-request-url false))]
    (when pr-id
      (-> (make-graphql-post access-token pull-request-query {:pullRequestId pr-id})
          :data
          :node))))

(defn modify-pull-request
  "Modify a pull request at the url with the provided mutation."
  ([access-token url mutation]
   (modify-pull-request access-token url mutation nil))
  ([access-token url mutation variables]
   (let [pr-id (or (get-open-pr-id access-token url) (get-pull-request-id access-token url false))]
     (when pr-id
       (let [merged-variables (merge variables {:pullRequestId pr-id})]
         (make-graphql-post access-token mutation merged-variables))))))

(defn modify-comment
  "Modify a comment at the url with the provided mutation and variables."
  [access-token url mutation variables]
  (let [comment-id (get-issue-comment-id access-token url)]
    (when comment-id
      (let [merged-variables (merge variables {:commentId comment-id})]
        (make-graphql-post access-token mutation merged-variables)))))


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
  request.

  Returns the pull request's permanent URL.
  "
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
  https://github.com/owner/name/pull/1) or
  partial (owner/name/pull/1) URL of the pull request.

  * updated -- a map describing the update. The keys: :title, :body.

  Returns the pull request's permanent URL.
  "
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
  https://github.com/owner/name/pull/1) or
  partial (owner/name/pull/1) URL of the pull request.

  Returns the pull request's permanent URL.
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
  https://github.com/owner/name/pull/1) or
  partial (owner/name/pull/1) URL of the pull request.

  * comment-body -- the comment to add.

  Returns the comment's permanent URL.
  "
  [access-token pull-request-url comment-body]
  (-> (modify-pull-request access-token pull-request-url add-comment-mutation {:body comment-body})
      :data
      :addComment
      :commentEdge
      :node
      :url))

(defn edit-pull-request-comment
  "Changes the body of a comment

  Arguments:
  * access-token -- the Github access token to use.

  * comment-url -- e.g., the full (e.g.,
  https://github.com/owner/name/pull/4#issuecomment-702092682) or
  partial (owner/name/pull/4#issuecomment-702092682) URL of the comment.

  * comment-body -- the new body of the comment.

  Returns the comment's permanent URL.
  "
  [access-token comment-url comment-body]
  (-> (modify-comment access-token comment-url edit-comment-mutation {:body comment-body})
      :data
      :updateIssueComment
      :issueComment
      :url))

(defn close-pull-request
  "Change the status of a pull request to closed.

  Arguments:
  * access-token -- the Github access token to use. Must
  have repo permissions.

  * pull-request-url -- the full (e.g.,
  https://github.com/owner/name/pull/1) or
  partial (owner/name/pull/1) URL of the pull request.
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
  https://github.com/owner/name/pull/1) or
  partial (owner/name/pull/1) URL of the pull request.
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
  https://github.com/owner/name/pull/1) or
  partial (owner/name/pull/1) URL of the pull request.

  * merge-options -- a map with keys that can include :title (the
  headline of the commit), :body (any body description of the
  commit), :mergeMethod (default \"SQUASH\", but can also be
  \"MERGE\" or \"REBASE\") and :authorEmail.

  All of these fields are optional."
  [access-token pull-request-url merge-options]
  (let [prinfo (get-pull-request-info access-token pull-request-url)
        expected-head-ref (:headRefOid prinfo)]
    (if expected-head-ref
      (let [opts (merge {:mergeMethod "SQUASH"} merge-options {:expectedHeadRef expected-head-ref})]
        (-> (modify-pull-request access-token pull-request-url merge-pull-request-mutation opts)
            :data
            :mergePullRequest
            :pullRequest
            :permalink))
      (throw (ex-info "Pull request not found" {:pullRequestUrl pull-request-url})))))
