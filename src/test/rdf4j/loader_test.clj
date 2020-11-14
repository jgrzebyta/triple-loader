(ns rdf4j.loader-test
  (:use [rdf4j.loader]
        [rdf4j.repository]
        [rdf4j.reifiers :only [counter-commiter]]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio])
  (:require [rdf4j.utils :as u]
            [rdf4j.core :as c])
  (:import [java.io File]
           [clojure.lang ExceptionInfo]
           [org.eclipse.rdf4j.model Resource Statement]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]
           [org.eclipse.rdf4j.repository.http HTTPRepository]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.sail.lucene LuceneSail]))


#_(deftest connect-triple 							; temporary switching off integration test
    (testing "Test initialising connection."
      (let [server-url "http://localhost:8080/openrdf-sesame"
            repository-id "test"
            init-connection-f #'rdf4j.loader/init-connection]                  ; access to prive function
        (with-open-repository (c (HTTPRepository. server-url repository-id))
          (init-connection-f c)
          (is (instance? org.eclipse.rdf4j.repository.RepositoryConnection c))
          (log/debug "Repository connection class: %s" (class c))
          (is (.isOpen c))))))

(deftest open-file
  (with-open [fr (jio/reader (-> "./resources/beet.rdf"
                                 (jio/resource)
                                 (.getPath)))]
    (println "reader?" (class fr))
    (testing "Is Reader instantiated"
      (is (instance? java.io.BufferedReader fr)))
    (testing "Reads any character"
      (let [lines (count (line-seq fr))]
        (is (= 175 lines))
        (println (format "File contains %d lines" lines)))
      )))


(defn test-repository "Does more detailed tests on storage" [^SailRepository repository expected]
  (log/debug "repository class: " (class repository))
  (with-open-repository [c repository]
    (let [result (u/get-all-statements c) ;(.getStatements c nil nil nil false (into-array Resource '[]))
          statement-total (count result)]
      (is (= expected statement-total))
      (log/debug (format "Found %d statements" statement-total))
      )))


(deftest load-mock-repo
  (let [repo (make-mem-repository)
        pars (Rio/createParser RDFFormat/RDFXML)
        file-obj (jio/file (jio/resource "./resources/beet.rdf"))
        counter (atom 0)]
    (testing "Loading data to repository"
      (.initialize repo)
      (with-open [conn (.getConnection repo)
                  fr (jio/reader file-obj)]
        ;; parse file
        (log/debug "start file parsing")
        (.setRDFHandler pars (counter-commiter conn counter))
        (.parse pars fr (.toString (.toURI file-obj)))
        (.commit conn)
        (is (not (.isEmpty conn)))
        (log/debug "Is Connection empty?: " (.isEmpty conn)))
      )
    (testing "Does more tests ... "
      (test-repository repo 68))
    (is (= 68 @counter))
    (.shutDown repo)
    (delete-context)))

(deftest test-delete-context
  (testing "Delete tmp repository"
    (let [repository (make-repository-with-lucene)
          tmp-dir (:path @context)]
      (is (instance? File tmp-dir))
      (is (.exists tmp-dir))  ;; repository still exists
      (delete-context)
      (log/debug "state after: " @context)  ;; repository is deleted
      )))

(deftest load-data-test
  (let [repo (make-repository-with-lucene nil)]
    (try
      (let [cont (c/load-data repo (-> "./resources/beet.rdf"
                                       (jio/resource)
                                       (.getPath)))]
        (test-repository repo 68)
        (is (= 68 cont)))
        (finally
          (.shutDown repo)
          (delete-context))
    )))

(deftest repository-deduping-test
  (testing "issue #15"
    (let [repo (make-repository-with-lucene)
          vf (u/value-factory)]
      (with-open-repository [cnx repo]
       (try 
         ;; load dirty data
         (.add cnx (.createIRI vf "urn:subject/1") (.createIRI vf "urn:predicate1") (.createLiteral vf "Ala") (make-array Resource 0))
         (.add cnx (.createIRI vf "urn:subject/1") (.createIRI vf "urn:predicate1") (.createLiteral vf "ma") (make-array Resource 0))
         (.add cnx (.createIRI vf "urn:subject/1") (.createIRI vf "urn:predicate1") (.createLiteral vf "kota") (make-array Resource 0))
         (.add cnx (.createIRI vf "urn:subject/1") (.createIRI vf "urn:predicate1") (.createLiteral vf "kota") (make-array Resource 0))
         (finally (.commit cnx))))
      ;; check number of triples
      (test-repository repo 3)
      (.shutDown repo)
      (delete-context))
    ))

(deftest test-errored-file
  (testing "simple test for issue #33"
    (let [file (-> "./resources/beet-error.trig"
                   (jio/resource)
                   (jio/file))
          repo (make-repository)]
      (try
        (is (thrown? Exception (c/load-data repo file)))
        (finally
          (.shutDown repo)
          (delete-context)))))
  (testing "deep test for issue #33"
    (let [file (-> "./resources/beet-error.trig"
                   (jio/resource)
                   (jio/file))
          repo (make-repository)]
      (try
        (c/load-data repo file)
        (catch Exception e
          (is (instance? ExceptionInfo e))
          (is (string? (get (ex-data e) :file) )))
        (finally
          (.shutDown repo)
          (delete-context))))))
