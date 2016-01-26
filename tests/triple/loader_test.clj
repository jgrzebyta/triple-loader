(ns triple.loader-test
  (:use [triple.loader]
        [triple.reifiers :only [chunk-commiter]]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio])
  (:import [info.aduna.iteration CloseableIteration]
           [org.openrdf.model Resource Statement]
           [org.openrdf.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.openrdf.repository RepositoryResult]
           [org.openrdf.repository.sail SailRepository]
           [org.openrdf.sail.memory MemoryStore]))

(defn count-statements "Counts statements in result." [^CloseableIteration result]
  (try
    (loop [count 0]
      (if (.hasNext result)
        (do
          (.next result)  ;; ignore result
          (recur (inc count)))
        count))
    (catch Exception e (log/error "Some error: " (.getMessage e)))
    (finally (.close result))))


(deftest connect-triple
  (testing "Test initialising connection."
    (let [server-url "http://localhost:8080/openrdf-sesame"
          repository-id "test"]
      (with-open [c (init-connection server-url repository-id)]
        (is (instance? org.openrdf.repository.RepositoryConnection c))
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


(defn make-mem-repository [& [store]]
  (doto
      (SailRepository. (if store store (MemoryStore.)))
    (.initialize)))

(defn test-repository "Doeas more detailed tests on storage" [repository expected]
  (is (instance? SailRepository repository))
  (log/debug "repository class: " (class repository))
  (with-open [c (.getConnection repository)]
    (let [result (.getStatements c nil nil nil (into-array Resource '[]))
          statement-total (count-statements result)]
      (is (= expected statement-total))
      (log/debug (format "Found %d statements" statement-total))
      )))

(deftest load-mock-repo
  (let [repo (make-mem-repository)
        pars (Rio/createParser RDFFormat/RDFXML)
        file-obj (jio/file "tests/beet.rdf")]
    (testing "Loading data to repository"
      (with-open [conn (.getConnection repo)
                  fr (jio/reader file-obj)]
        ;; parse file
        (log/debug "start file parsing")
;;      (log/debug "")
        (.setRDFHandler pars (chunk-commiter conn))
        (.parse pars fr (.toString (.toURI file-obj)))
        (.commit conn)
        (is (not (.isEmpty conn)))
        (log/debug "Is Connection empty?: " (.isEmpty conn)))
      )
    (testing "Does more tests ... "
      (test-repository repo 68)))
  )
