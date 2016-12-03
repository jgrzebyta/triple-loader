(ns rdf4j.loader-test
  (:use [rdf4j.loader]
        [rdf4j.repository]
        [rdf4j.reifiers :only [chunk-commiter]]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio])
  (:import [java.io File]
           [org.eclipse.rdf4j.model Resource Statement]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]
           [org.eclipse.rdf4j.repository.http HTTPRepository]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
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
  (with-open [fr (jio/reader "tests/beet.rdf")]
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
    (let [result (get-all-statements c) ;(.getStatements c nil nil nil false (into-array Resource '[]))
          statement-total (count result)]
      (is (= expected statement-total))
      (log/debug (format "Found %d statements" statement-total))
      )))


(deftest load-mock-repo
  (let [repo (make-mem-repository)
        pars (Rio/createParser RDFFormat/RDFXML)
        file-obj (jio/file "tests/beet.rdf")]
    (testing "Loading data to repository"
      (.initialize repo)
      (with-open [conn (.getConnection repo)
                  fr (jio/reader file-obj)]
        ;; parse file
        (log/debug "start file parsing")
        (.setRDFHandler pars (chunk-commiter conn))
        (.parse pars fr (.toString (.toURI file-obj)))
        (.commit conn)
        (is (not (.isEmpty conn)))
        (log/debug "Is Connection empty?: " (.isEmpty conn)))
      )
    (testing "Does more tests ... "
      (test-repository repo 68))
    (.shutDown repo)))

(deftest test-delete-temp-repository
  (testing "Delete tmp repository"
    (let [repository (make-repository-with-lucene)
          tmp-dir (@temp-repository :path)]
      (is (instance? File tmp-dir))
      (is (.exists tmp-dir))  ;; repository still exists
      (delete-temp-repository)
      (log/debug "state after: " @temp-repository)  ;; repository is deleted
      )))

(deftest load-data-test
  (let [repo (make-repository-with-lucene nil)]
    (try 
      (load-data repo "tests/beet.rdf")
      (test-repository repo 68)
      (finally
        (.shutDown repo)
        (delete-temp-repository))
    )))

(deftest repository-deduping-test
  (testing "issue #15"
    (let [repo (make-repository-with-lucene)
          vf (value-factory)]
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
      (delete-temp-repository))
    ))