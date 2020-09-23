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
    (testing "returns nil on error"
      (is (= nil (sut/get-repo-id "secret-token" "owner" "repo-name"))))))

(defn make-fake-post
  [query-response mutation-response]
  (fn [_ payload _] (if (string/includes? payload "query")
                      {:body query-response}
                      {:body mutation-response})))

(deftest test-createpr
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success)]
    (testing "Creates a pull request and returns the id"
      (is (= "MDExOlB1bGxSZXF1ZXN0NDkxMDgxOTQw"
             (sut/createpr "secret-token" "owner" "repo-name" "some title" "A body" "main" "new-stuff" true)))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-failure)]
    (testing "Returns nil on create error"
      (is (= nil
             (sut/createpr "secret-token" "owner" "repo-name" "some title" "A body" "main" "new-stuff" true)))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-failure nil)]
    (testing "Returns nil on failure to get repo id"
      (is (= nil
             (sut/createpr "secret-token" "owner" "repo-name" "some title" "A body" "main" "new-stuff" true))))))
