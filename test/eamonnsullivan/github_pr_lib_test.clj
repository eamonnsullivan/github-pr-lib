(ns eamonnsullivan.github-pr-lib-test
  (:require [clojure.test :refer :all]
            [eamonnsullivan.github-pr-lib :as sut]
            [clojure.string :as string]
            [clojure.data.json :as json]))

(def repo-id-response-success (slurp "./test/eamonnsullivan/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/repo-response-failure.json"))
(def create-pr-response-success (slurp "./test/eamonnsullivan/create-pr-response-success.json"))
(def create-pr-response-failure (slurp "./test/eamonnsullivan/create-pr-response-failure.json"))

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
      (is (= false (:draft variables)))
      (is (= true (:maintainerCanModify variables))))))

(defn assert-payload-defaults-overridden
  [payload]
  (testing "the draft and maintainerCanModify parameters can be overridden"
    (let [variables (:variables (json/read-str payload :key-fn keyword))]
      (is (= true (:draft variables)))
      (is (= false (:maintainerCanModify variables))))))

(deftest test-createpr
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil nil)]
    (testing "Creates a pull request and returns the id"
      (is (= "MDExOlB1bGxSZXF1ZXN0NDkxMDgxOTQw"
             (sut/createpr "secret-token" {:owner "owner"
                                           :name "repo-name"
                                           :title "some title"
                                           :body "A body"
                                           :base "main"
                                           :branch "new-stuff"
                                           :draft true})))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-failure nil nil)]
    (testing "Throws exception on create error"
      (is (thrown-with-msg? RuntimeException #"A pull request already exists for eamonnsullivan:create."
                            (sut/createpr "secret-token" {:owner "owner"
                                                          :name "repo-name"
                                                          :title "some title"
                                                          :body "A body"
                                                          :base "main"
                                                          :branch "new-stuff"
                                                          :draft true})))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-failure nil nil nil)]
    (testing "Throws exception on failure to get repo id"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'eamonnsullivan/not-there'."
                            (sut/createpr "secret-token" {:owner "owner"
                                                          :name "repo-name"
                                                          :title "some title"
                                                          :body "A body"
                                                          :base "main"
                                                          :branch "new-stuff"
                                                          :draft true})))))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil mutation-payload-assert)]
    (testing "The draft option defaults to false"
      (sut/createpr "secret-token" {:owner "owner"
                                    :name "repo-name"
                                    :title "some title"
                                    :body "A body"
                                    :base "main"
                                    :branch "new-stuff"}))
    (testing "The maintainerCanModify option defaults to true"
      (sut/createpr "secret-token" {:owner "owner"
                                    :name "repo-name"
                                    :title "some title"
                                    :body "A body"
                                    :base "main"
                                    :branch "new-stuff"})))
  (with-redefs [sut/http-post (make-fake-post repo-id-response-success create-pr-response-success nil assert-payload-defaults-overridden)]
    (testing "The defaulted options can be overridden"
      (sut/createpr "secret-token" {:owner "owner"
                                    :name "repo-name"
                                    :title "some title"
                                    :body "A body"
                                    :base "main"
                                    :branch "new-stuff"
                                    :draft true
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
