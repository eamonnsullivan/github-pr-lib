(ns eamonnsullivan.github-pr-lib-test
  (:require [clojure.test :refer :all]
            [eamonnsullivan.github-pr-lib :as sut]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def repo-id-response-success (slurp "./test/eamonnsullivan/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/repo-response-failure.json"))
(def create-pr-response-success (slurp "./test/eamonnsullivan/create-pr-response-success.json"))
(def create-pr-response-failure (slurp "./test/eamonnsullivan/create-pr-response-failure.json"))
(def first-page (slurp "./test/eamonnsullivan/first-page-pr.json"))
(def second-page (slurp "./test/eamonnsullivan/second-page-pr.json"))
(def update-pr-success (slurp "./test/eamonnsullivan/update-pr-success.json"))
(def update-pr-failure (slurp "./test/eamonnsullivan/update-pr-failure.json"))
(def mark-ready-success (slurp "./test/eamonnsullivan/mark-ready-success.json"))
(def mark-ready-failure (slurp "./test/eamonnsullivan/mark-ready-failure.json"))
(def add-comment-success (slurp "./test/eamonnsullivan/add-comment-success.json"))
(def add-comment-failure (slurp "./test/eamonnsullivan/add-comment-failure.json"))
(def close-pull-request-success (slurp "./test/eamonnsullivan/close-pull-request-success.json"))
(def close-pull-request-failure (slurp "./test/eamonnsullivan/close-pull-request-failure.json"))
(def reopen-pull-request-success (slurp "./test/eamonnsullivan/reopen-pull-request-success.json"))
(def reopen-pull-request-failure (slurp "./test/eamonnsullivan/reopen-pull-request-failure.json"))
(def merge-pull-request-success (slurp "./test/eamonnsullivan/merge-pull-request-success.json"))
(def merge-pull-request-failure (slurp "./test/eamonnsullivan/merge-pull-request-failure.json"))

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
      (is (= true (:draft variables)))
      (is (= true (:maintainerCanModify variables))))))

(defn assert-payload-defaults-overridden
  [payload]
  (testing "the draft and maintainerCanModify parameters can be overridden"
    (let [variables (:variables (json/read-str payload :key-fn keyword))]
      (is (= false (:draft variables)))
      (is (= false (:maintainerCanModify variables))))))

(deftest test-create-pull-request
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil nil)]
    (testing "Creates a pull request and returns the id"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/1"
             (sut/create-pull-request "secret-token" {:owner "owner"
                                                      :name "repo-name"
                                                      :title "some title"
                                                      :body "A body"
                                                      :base "main"
                                                      :branch "new-stuff"
                                                      :draft false})))))
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
                                               :branch "new-stuff"}))
    (testing "The maintainerCanModify option defaults to true"
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
                                               :draft false
                                               :maintainerCanModify false}))))


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

(defn fake-paging-post
  []
  (fn [_ payload _]
    (let [variables (:variables (json/read-str payload :key-fn keyword))
          after (:after variables)]
      (if after
        {:body second-page}
        {:body first-page}))))

(deftest test-get-pr-id
  (with-redefs [sut/http-post (fake-paging-post)]
    (testing "finds pull request id"
      (is (= "MDExOlB1bGxSZXF1ZXN0MTU5NjI3ODc2" (sut/get-open-pr-id "secret" "https://github.com/eamonnsullivan/something/pull/2")))
      (is (= "MDExOlB1bGxSZXF1ZXN0MTYwOTE1ODA0" (sut/get-open-pr-id "secret" "https://github.com/eamonnsullivan/something/pull/5"))))
    (testing "finds pull request id on subsequent pages"
      (is (= "MDExOlB1bGxSZXF1ZXN0MTU5NjI3ODc2" (sut/get-open-pr-id "secret" "https://github.com/eamonnsullivan/something/pull/7"))))))

(deftest test-update-pull-request
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body update-pr-success})]
    (testing "updates a pull request"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/3" (sut/update-pull-request "secret"
                                                                                               "https://github.com/eamonnsullivan/github-pr-lib/pull/3"
                                                                                               {:title "A new title" :body "A new body"})))))
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body update-pr-failure})]
    (testing "Throws exception on update error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid-id'"
                            (sut/update-pull-request "secret"
                                                     "https://github.com/eamonnsullivan/github-pr-lib/pull/3"
                                                     {:title "A new title" :body "A new body"}))))))
(deftest test-mark-ready-for-review
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body mark-ready-success})]
    (testing "marks pull request as ready for review"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/3" (sut/mark-ready-for-review "secret"
                                                                                                 "https://github.com/eamonnsullivan/github-pr-lib/pull/3")))))
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body mark-ready-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid-id'"
                            (sut/mark-ready-for-review "secret"
                                                       "https://github.com/eamonnsullivan/github-pr-lib/pull/3"))))))

(deftest test-add-pull-request-comment
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body add-comment-success})]
    (testing "adds comment to pull request"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702076146" (sut/add-pull-request-comment "secret"
                                                                                                                           "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                                                                                           "This is a comment.")))))
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body add-comment-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/add-pull-request-comment "secret"
                                                          "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                          "This is a comment."))))))

(deftest test-close-pull-request
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body close-pull-request-success})]
    (testing "closes a pull request"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4" (sut/close-pull-request "secret"
                                                                                              "https://github.com/eamonnsullivan/github-pr-lib/pull/4")))))
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some-id")
                sut/http-post (fn [_ _ _] {:body close-pull-request-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/close-pull-request "secret"
                                                    "https://github.com/eamonnsullivan/github-pr-lib/pull/4"))))))

(deftest test-reopen-pull-request
  (with-redefs [sut/get-open-pr-id (fn [_ _] nil)
                sut/get-pull-request-id (fn [_ _ _] "some-id")
                sut/http-post (fn [_ _ _] {:body reopen-pull-request-success})]
    (testing "reopens a pull request"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4" (sut/reopen-pull-request "secret"
                                                                                               "https://github.com/eamonnsullivan/github-pr-lib/pull/4")))))
  (with-redefs [sut/get-open-pr-id (fn [_ _] nil)
                sut/get-pull-request-id (fn [_ _ _] "some-id")
                sut/http-post (fn [_ _ _] {:body reopen-pull-request-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/reopen-pull-request "secret"
                                                     "https://github.com/eamonnsullivan/github-pr-lib/pull/4"))))))

(deftest test-merge-pull-request
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some id")
                sut/http-post (fn [_ _ _] {:body merge-pull-request-success})]
    (testing "merges a pull request"
      (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4" (sut/merge-pull-request "secret"
                                                                                              "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                                                              {:title "a commit" :body "some description" :merge-method "SQUASH"
                                                                                               :author-email "someone@somewhere.com"
                                                                                               :expected-head-ref "c61a00376ff448c43c38a6443f932f7187e027ce"})))))
  (with-redefs [sut/get-open-pr-id (fn [_ _] "some id")
                sut/http-post (fn [_ _ _] {:body merge-pull-request-failure})]
    (testing "Throws exception on error"
      (is (thrown-with-msg? RuntimeException #"Could not resolve to a node with the global id of 'invalid'"
                            (sut/merge-pull-request "secret"
                                                    "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
                                                    {:title "a commit" :body "some description" :merge-method "SQUASH"
                                                     :author-email "someone@somewhere.com"
                                                     :expected-head-ref "c61a00376ff448c43c38a6443f932f7187e027ce"}))))))
