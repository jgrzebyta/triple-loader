(ns triple.context-test
  (:use [triple.loader-test :as ltest])
  (:import [org.openrdf.query QueryLanguage]))


(def context-string "urn:graph/beet")

(deftest context-loading
  (testing "Load data into named graph"
    (let [q1 "select * where {?s ?p ?o}"
          q2 (str "select * from <" context-string "> where {?s ?p ?o}")]
      (ltest/with-open-rdf-context cnx RDFFormat/RDFXML "tests/beet.rdf" context-string
        (let [tq1 (.prepareTupleQuery cnx QueryLanguage/SPARQL q1)
              tq2 (.prepareTupleQuery cnx QueryLanguage/SPARQL q2)]
          (try
            (let [r1 (.evaluate tq1)]
              
              )
            ))
        ))))
