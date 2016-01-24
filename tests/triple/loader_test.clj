(ns triple.loader-test
  (:use [triple.loader]
        [clojure.test]
        [clojure.java.io :as jio]))

(deftest connect-triple
  (testing "Test initialising connection."
    (let [server-url "http://localhost:8080/openrdf-sesame"
          repository-id "test"]
      (with-open [c (init-connection server-url repository-id)]
        (is (instance? org.openrdf.repository.RepositoryConnection c))
        (println "Repository connection class: " (class c))
        (is (.isOpen c))))))

(deftest open-file
  (with-open [fr (jio/reader "tests/beet.rdf")]
    (println "reader?" (class fr))
    (testing "Is Reader instantiated"
      (is (instance? java.io.BufferedReader fr)))
    (testing "Reads any character"
      (let [lines (count (line-seq fr))]
        (is (= 175 lines))
        (println (format "File contains %d lines" lines)))
      )))
