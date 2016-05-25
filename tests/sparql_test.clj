(ns sparql-test
  (:gen-class)
  (:use [sparql]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
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
