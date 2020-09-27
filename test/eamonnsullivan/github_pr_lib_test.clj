(ns eamonnsullivan.github-pr-lib-test
  (:require [clojure.test :refer :all]
            [eamonnsullivan.github-pr-lib :as sut]
            [clojure.string :as string]))

(def repo-id-response-success (slurp "./test/eamonnsullivan/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/repo-response-failure.json"))
(def create-pr-response-success (slurp "./test/eamonnsullivan/create-pr-response-success.json"))
(def create-pr-response-failure (slurp "./test/eamonnsullivan/create-pr-response-failure.json"))

(deftest test-get-repo-id
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-success})]
    (testing "parses id from successful response"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner" "repo-name")))))
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-failure})]
    (testing "throws exception on error"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'eamonnsullivan/not-there'."
                            (sut/get-repo-id "secret-token" "owner" "repo-name"))))))

(defn make-fake-post
  [query-response mutation-response]
  (fn [_ payload _] (if (string/includes? payload "mutation")
                      {:body mutation-response}
                      {:body query-response})))

(deftest test-createpr
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success)]
    (testing "Creates a pull request and returns the id"
      (is (= "MDExOlB1bGxSZXF1ZXN0NDkxMDgxOTQw"
             (sut/createpr "secret-token" "owner" "repo-name" "some title" "A body" "main" "new-stuff" true)))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-failure)]
    (testing "Throws exception on create error"
      (is (thrown-with-msg? RuntimeException #"A pull request already exists for eamonnsullivan:create."
             (sut/createpr "secret-token" "owner" "repo-name" "some title" "A body" "main" "new-stuff" true)))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-failure nil)]
    (testing "Throws exception on failure to get repo id"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'eamonnsullivan/not-there'."
                            (sut/createpr "secret-token" "owner" "repo-name" "some title" "A body" "main" "new-stuff" true))))))

(deftest test-get-owner-and-name
  (testing "Finds owner and repo name from github url"
    (is (= {:owner "eamonnsullivan" :name "emacs.d"} (sut/get-owner-and-name "https://github.com/eamonnsullivan/emacs.d")))
    (is (= {:owner "eamonnsullivan" :name "github-search"} (sut/get-owner-and-name "https://github.com/eamonnsullivan/github-search/blob/master/src/eamonnsullivan/github_search.clj")))
    (is (= {:owner "eamonnsullivan" :name "github-pr-lib"} (sut/get-owner-and-name "https://github.com/eamonnsullivan/github-pr-lib/pull/1")))
    (is (= {:owner "bbc" :name "optimo"} (sut/get-owner-and-name "https://github.com/bbc/optimo/pull/1277"))))
  (testing "Returns nil when the url is incomplete or unrecognised"
    (is (= nil (sut/get-owner-and-name "something else")))
    (is (= nil (sut/get-owner-and-name "https://github.com/bbc/")))))
