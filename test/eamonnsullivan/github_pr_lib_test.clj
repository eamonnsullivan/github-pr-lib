(ns eamonnsullivan.github-pr-lib-test
  (:require [clojure.test :refer :all]
            [eamonnsullivan.github-pr-lib :as sut]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def repo-id-response-success (slurp "./test/eamonnsullivan/fixtures/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/fixtures/repo-response-failure.json"))
(def create-pr-response-success (slurp "./test/eamonnsullivan/fixtures/create-pr-response-success.json"))
(def create-pr-response-failure (slurp "./test/eamonnsullivan/fixtures/create-pr-response-failure.json"))
(def update-pr-success (slurp "./test/eamonnsullivan/fixtures/update-pr-success.json"))
(def update-pr-failure (slurp "./test/eamonnsullivan/fixtures/update-pr-failure.json"))
(def mark-ready-success (slurp "./test/eamonnsullivan/fixtures/mark-ready-success.json"))
(def mark-ready-failure (slurp "./test/eamonnsullivan/fixtures/mark-ready-failure.json"))
(def add-comment-success (slurp "./test/eamonnsullivan/fixtures/add-comment-success.json"))
(def add-comment-failure (slurp "./test/eamonnsullivan/fixtures/add-comment-failure.json"))
(def edit-comment-success (slurp "./test/eamonnsullivan/fixtures/edit-comment-success.json"))
(def edit-comment-failure (slurp "./test/eamonnsullivan/fixtures/edit-comment-failure.json"))
(def close-pull-request-success (slurp "./test/eamonnsullivan/fixtures/close-pull-request-success.json"))
(def close-pull-request-failure (slurp "./test/eamonnsullivan/fixtures/close-pull-request-failure.json"))
(def reopen-pull-request-success (slurp "./test/eamonnsullivan/fixtures/reopen-pull-request-success.json"))
(def reopen-pull-request-failure (slurp "./test/eamonnsullivan/fixtures/reopen-pull-request-failure.json"))
(def merge-pull-request-success (slurp "./test/eamonnsullivan/fixtures/merge-pull-request-success.json"))
(def merge-pull-request-failure (slurp "./test/eamonnsullivan/fixtures/merge-pull-request-failure.json"))
(def pull-request-properties (slurp "./test/eamonnsullivan/fixtures/pull-request-properties.json"))

(defn has-value
  [key value]
  (fn [m]
    (= value (m key))))

(deftest test-get-repo-id
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-success})]
    (testing "parses id from successful response"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner" "repo-name"))))
    (testing "handles url instead of separate owner/repo-name"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner/repo-name")))
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "https://github.com/owner/repo-name")))))
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-failure})]
    (testing "throws exception on error"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'eamonnsullivan/not-there'."
                            (sut/get-repo-id "secret-token" "owner" "repo-name"))))))

(defn make-fake-post
  [query-response mutation-response query-asserts mutation-asserts]
  (fn [_ payload _] (if (string/includes? payload "mutation")
                      (do (and mutation-asserts (mutation-asserts payload))
                          {:body mutation-response})
                      (do (and query-asserts (query-asserts payload))
                          {:body query-response}))))

(defn mutation-payload-assert
  [payload]
  (testing "the draft and maintainerCanModfy parameters have a default"
    (let [variables (:variables (json/read-str payload :key-fn keyword))]
      (is (= true (:draft variables))))))

(defn assert-payload-defaults-overridden
  [payload]
  (testing "the draft parameter can be overridden"
    (let [variables (:variables (json/read-str payload :key-fn keyword))]
      (is (= false (:draft variables))))))

(deftest test-create-pull-request
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil nil)]
    (testing "Creates a pull request and returns the id"
      (let [response (sut/create-pull-request "secret-token" {:owner "owner"
                                                              :name "repo-name"
                                                              :title "some title"
                                                              :body "A body"
                                                              :base "main"
                                                              :branch "new-stuff"
                                                              :draft false})]
        (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/1") response)
        (is (has-value :isDraft false) response)
        (is (has-value :body "A body") response)
        (is (has-value :merged false) response))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-failure nil nil)]
    (testing "Throws exception on create error"
      (is (thrown-with-msg? RuntimeException #"A pull request already exists for eamonnsullivan:create."
                            (sut/create-pull-request "secret-token" {:owner "owner"
                                                                     :name "repo-name"
                                                                     :title "some title"
                                                                     :body "A body"
                                                                     :base "main"
                                                                     :branch "new-stuff"
                                                                     :draft false})))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-failure nil nil nil)]
    (testing "Throws exception on failure to get repo id"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'eamonnsullivan/not-there'."
                            (sut/create-pull-request "secret-token" {:owner "owner"
                                                                     :name "repo-name"
                                                                     :title "some title"
                                                                     :body "A body"
                                                                     :base "main"
                                                                     :branch "new-stuff"
                                                                     :draft false})))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil mutation-payload-assert)]
    (testing "The draft option defaults to true"
      (sut/create-pull-request "secret-token" {:owner "owner"
                                               :name "repo-name"
                                               :title "some title"
                                               :body "A body"
                                               :base "main"
                                               :branch "new-stuff"})))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil assert-payload-defaults-overridden)]
    (testing "The defaulted options can be overridden"
      (sut/create-pull-request "secret-token" {:owner "owner"
                                               :name "repo-name"
                                               :title "some title"
                                               :body "A body"
                                               :base "main"
                                               :branch "new-stuff"
                                               :draft false}))))


(deftest test-parse-repo
  (testing "Finds owner and repo name from github url"
    (is (= {:owner "eamonnsullivan" :name "emacs.d"} (sut/parse-repo "https://github.com/eamonnsullivan/emacs.d")))
    (is (= {:owner "eamonnsullivan" :name "github-search"} (sut/parse-repo "https://github.com/eamonnsullivan/github-search/blob/master/src/eamonnsullivan/github_search.clj")))
    (is (= {:owner "eamonnsullivan" :name "github-pr-lib"} (sut/parse-repo "https://github.com/eamonnsullivan/github-pr-lib/pull/1")))
    (is (= {:owner "bbc" :name "optimo"} (sut/parse-repo "https://github.com/bbc/optimo/pull/1277"))))
  (testing "github hostname is optional"
    (is (= {:owner "eamonnsullivan" :name "emacs.d"} (sut/parse-repo "eamonnsullivan/emacs.d")))
    (is (= {:owner "eamonnsullivan" :name "github-search"} (sut/parse-repo "eamonnsullivan/github-search/blob/master/src/eamonnsullivan/github_search.clj")))
    (is (= {:owner "eamonnsullivan" :name "github-pr-lib"} (sut/parse-repo "eamonnsullivan/github-pr-lib/pull/1")))
    (is (= {:owner "bbc" :name "optimo"} (sut/parse-repo "bbc/optimo/pull/1277"))))
  (testing "Returns nil when the url is incomplete or unrecognised"
    (is (= nil (sut/parse-repo "something else")))
    (is (= nil (sut/parse-repo "https://github.com/bbc/")))))

(deftest test-pull-request-number
  (testing "Finds the pull request number when given a url"
    (is (= 1278 (sut/pull-request-number "https://github.com/owner/name/pull/1278")))
    (is (= 8 (sut/pull-request-number "https://github.com/owner/name/pull/8")))
    (is (= 1278456 (sut/pull-request-number "https://github.com/owner/name/pull/1278456"))))
  (testing "Returns nil if no pull request number is found"
    (is (= nil (sut/pull-request-number "something else")))
    (is (= nil (sut/pull-request-number "https://github/owner/name/pull")))))


(deftest test-get-pull-request-id
  (with-redefs [sut/http-get (fn [_ _ _] {:body "{\"node_id\": \"a-node-id\"}"})]
    (testing "finds the node id in the body"
      (is (= "a-node-id" (sut/get-pull-request-id "secret" "https://github.com/owner/name/pull/1")))))
  (with-redefs [sut/http-get (fn [_ _ _] {:body "{}"})]
    (testing "Throws exception on failure"
      (is (thrown-with-msg? RuntimeException #"Could not find pull request: https://github.com/owner/name/pull/2"
                            (sut/get-pull-request-id "secret" "https://github.com/owner/name/pull/2"))))))

(deftest test-get-issue-comment-id
  (with-redefs [sut/http-get (fn [_ _ _] {:body "{\"node_id\": \"a-node-id\"}"})]
    (testing "finds the node id in the body"
      (is (= "a-node-id" (sut/get-issue-comment-id "secret" "https://github.com/owner/name/pull/1#issuecomment-1")))))
  (with-redefs [sut/http-get (fn [_ _ _] {:body "{}"})]
    (testing "Throws exception on failure"
      (is (thrown-with-msg? RuntimeException #"Could not find comment: https://github.com/owner/name/pull/1#issuecomment-1"
                            (sut/get-issue-comment-id "secret" "https://github.com/owner/name/pull/1#issuecomment-1"))))))

(deftest test-update-pull-request
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body update-pr-success})]
    (testing "updates a pull request"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/3")
          (sut/update-pull-request "secret"
                                   "https://github.com/eamonnsullivan/github-pr-lib/pull/3"
                                   {:title "A new title" :body "A new body"}))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body update-pr-failure})]
    (testing "Throws exception on update error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid-id'"
                            (sut/update-pull-request "secret"
                                                     "https://github.com/eamonnsullivan/github-pr-lib/pull/3"
                                                     {:title "A new title" :body "A new body"}))))))
(deftest test-mark-ready-for-review
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body mark-ready-success})]
    (testing "marks pull request as ready for review"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/3")
             (sut/mark-ready-for-review
              "secret"
              "https://github.com/eamonnsullivan/github-pr-lib/pull/3"))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body mark-ready-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid-id'"
                            (sut/mark-ready-for-review "secret"
                                                       "https://github.com/eamonnsullivan/github-pr-lib/pull/3"))))))

(deftest test-add-pull-request-comment
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body add-comment-success})]
    (testing "adds comment to pull request"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702076146")
          (sut/add-pull-request-comment
           "secret"
           "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           "This is a comment."))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body add-comment-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/add-pull-request-comment "secret"
                                                          "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                          "This is a comment."))))))

(deftest test-edit-pull-request-comment
  (with-redefs [sut/get-pull-request-id (fn [_ _ _] "some-id")
                sut/get-issue-comment-id (fn [_ _] "comment-id")
                sut/http-post (fn [_ _ _] {:body edit-comment-success})]
    (testing "edits comment on pull request"
      (is (has-value :url "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702069017")
          (sut/edit-pull-request-comment
           "secret"
           "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702069017"
           "This is an updated comment."))))
  (with-redefs [sut/get-pull-request-id (fn [_ _ _] "some-id")
                sut/get-issue-comment-id (fn [_ _] "comment-id")
                sut/http-post (fn [_ _ _] {:body edit-comment-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/edit-pull-request-comment
                             "secret"
                             "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702076146"
                             "This is an updated comment."))))))

(deftest test-close-pull-request
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body close-pull-request-success})]
    (testing "closes a pull request"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/4")
          (sut/close-pull-request
           "secret"
           "https://github.com/eamonnsullivan/github-pr-lib/pull/4"))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body close-pull-request-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/close-pull-request "secret"
                                                    "https://github.com/eamonnsullivan/github-pr-lib/pull/4"))))))

(deftest test-reopen-pull-request
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body reopen-pull-request-success})]
    (testing "reopens a pull request"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/4")
          (sut/reopen-pull-request
           "secret"
           "https://github.com/eamonnsullivan/github-pr-lib/pull/4"))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body reopen-pull-request-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/reopen-pull-request "secret"
                                                     "https://github.com/eamonnsullivan/github-pr-lib/pull/4"))))))

(defn assert-merge-payload-defaults
  [payload]
  (testing "the mergeMethod and expectedHeadRef are supplied"
    (let [variables (:variables (json/read-str payload :key-fn keyword))]
      (is (not= nil (:mergeMethod variables)))
      (is (not= nil (:expectedHeadRef variables))))))

(deftest test-merge-pull-request
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some id")
                sut/get-pull-request-info (fn [_ _] {:headRefOid "commit-id"})
                sut/http-post (fn [_ _ _] {:body merge-pull-request-success})]
    (testing "merges a pull request"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/4")
          (sut/merge-pull-request
           "secret"
           "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           {:title "a commit" :body "some description"
            :author-email "someone@somewhere.com"}))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some id")
                sut/get-pull-request-info (fn [_ _] {:headRefOid "commit-id"})
                sut/http-post (fn [_ _ _] {:body merge-pull-request-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/merge-pull-request "secret"
                                                    "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                    {:title "a commit" :body "some description"
                                                     :author-email "someone@somewhere.com"})))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some id")
                sut/get-pull-request-info (fn [_ _] nil)
                sut/http-post (fn [_ _ _] {:body merge-pull-request-failure})]
    (testing "Throws exception if the pull request can't be found"
      (is (thrown-with-msg? RuntimeException #"Pull request not found"
                            (sut/merge-pull-request "secret"
                                                    "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                    {:title "a commit" :body "some description"
                                                     :author-email "someone@somewhere.com"})))))
  (with-redefs [sut/get-pull-request-id (fn [_ _] "some id")
                sut/get-pull-request-info (fn [_ _] {:headRefOid "commit-id"})
                sut/http-post (make-fake-post nil merge-pull-request-success nil assert-merge-payload-defaults)]
    (testing "merges a pull request with defaults"
      (is (has-value :permalink "https://github.com/eamonnsullivan/github-pr-lib/pull/4")
          (sut/merge-pull-request
           "secret"
           "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           {:title "a commit" :body "some description"
            :author-email "someone@somewhere.com"})))))

(deftest test-get-pr-from-comment-url
  (testing "parses a pull request url from a comment url"
    (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           (:pullRequestUrl (sut/parse-comment-url "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682"))))
    (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           (:pullRequestUrl (sut/parse-comment-url "eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682"))))
    (is (= "702092682"
           (:issueComment (sut/parse-comment-url "eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682")))))
  (testing "return nil when a pull request url can't be found"
    (is (= nil
           (:pullRequestUrl (sut/parse-comment-url "https://news.bbc.co.uk"))))))

(deftest test-get-pull-request-info
  (with-redefs [sut/http-post (fn [_ _ _] {:body pull-request-properties})
                sut/get-pull-request-id (fn [_ _] "some-id")]
    (let [response (sut/get-pull-request-info "secret" "owner/name/pull/1")]
      (is (= "A test title" (:title response)))
      (is (= "With a body" (:body response)))
      (is (= "johnsmith" (-> response :author :login)))
      (is (= "https://github.com/owner/name" (-> response :repository :url)))
      (is (= false (:isDraft response)))
      (is (= "MERGED" (:state response)))
      (is (= true (:merged response)))
      (is (= "UNKNOWN" (:mergeable response)))
      (is (= 9 (:number response))))))
