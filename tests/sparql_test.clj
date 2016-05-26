(ns sparql-test
  (:gen-class)
  (:use [sparql]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.string :as st :exclude [reverse replace]]
        [clojure.test]
        [triple.repository]
        [triple.loader-test])
  (:import [org.eclipse.rdf4j.rio RDFFormat]))


(deftest test-load-data 
  (let [repo (make-repository-with-lucene)]
    (load-data repo "tests/beet.rdf" RDFFormat/RDFXML)
  (testing "Count number of triples in repository"
    (test-repository repo 68))))


(deftest test-sparql-query
  (let [repo (make-repository-with-lucene)]
    (load-data repo "tests/beet.rdf" RDFFormat/RDFXML)
    (testing "execute simple SPARQL query"
      (let [sparql-str "select * where {?s ?p ?o}"]
        (with-open-repository [cx repo]
          (println "\n")
          (process-sparql-query cx sparql-str))))
    
    (testing "execute lucene SPARQL query"
      (let [sparql-str "PREFIX search: <http://www.openrdf.org/contrib/lucenesail#>  select * where { ?sub search:matches [search:query \"Germany\"; search:property <file:/tmp2/beet-1.csvCountries>; search:score ?score]}"]
        (with-open-repository [cx repo]
          (println "\n")
          (process-sparql-query cx sparql-str))
        ))))

(deftest test-load-sparql "Test load-sparql function"
  (testing "sparql as string"
    (let [sparql-string "select * where {?s ?p ?o}"
          sparql-processed (load-sparql sparql-string)]
      (is (string? sparql-processed) "processed sparql not string")
      (is (not (st/blank? sparql-processed)) "processed sparql is blank")))
  (testing "sparql from string"
    (let [file-path "tests/example1.sparql"
          sparql-processed (load-sparql file-path)]
      (log/debug (format "SPAQRL processed:\n==========\n %s \n=========\n" sparql-processed))
      (is (< 10 (count sparql-processed)))
      (is (string? sparql-processed) "processed sparql not string")
      (is (not (st/blank? sparql-processed)) "processed sparql is blank")))
  (testing "wrong sparql string"
    (let [sparql-string "seect * where {?s ?p ?o}"]
      (is (thrown? RuntimeException (load-sparql sparql-string)))))
  (testing "sparql from non existing file"
    (let [sparql-file "nonexists.sparql"]
      (is (thrown? RuntimeException (load-sparql sparql-file))))))
