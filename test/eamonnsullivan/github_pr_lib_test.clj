(ns eamonnsullivan.github-pr-lib-test
  (:require [clojure.test :refer :all]
            [eamonnsullivan.github-pr-lib :as sut]
            [clojure.data.json :as json]))

(def repo-id-response-success (slurp "./test/eamonnsullivan/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/repo-response-failure.json"))

(deftest test-get-repo-id
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-success})]
    (testing "parses id from successful response"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner" "repo-name")))))
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-failure})]
    (testing "returns nil on error"
      (is (= nil (sut/get-repo-id "secret-token" "owner" "repo-name"))))))
