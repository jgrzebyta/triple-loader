(ns triple.context-test
  (:use [triple.loader-test :as ltest]
        [clojure.test]
        [triple.utils :only [iter-seq]])
  (:import [org.openrdf.query QueryLanguage]
           [org.openrdf.rio Rio RDFFormat ParserConfig]))


(def context-string "urn:graph/beet")

(deftest load-mock-repo
  (let [repo (make-mem-repository)
        pars (Rio/createParser RDFFormat/RDFXML)
        file-obj (jio/file "tests/beet.rdf")]
    (testing "Loading data to repository"
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
      (test-repository repo 68))))




(deftest context-loading
  (testing "Load data into named graph"
    (let [q1 "select * where {?s ?p ?o}"
          q2 (str "select * from <" context-string "> where {?s ?p ?o}")
          repo (ltest/make-mem-repository)
          pars (Rio/createParser RDFFormat/RDFXML)
          file-obj (jio/file "tests/beet.rdf")]
      (with-open [conn (.getConnection repo)
                  fr (jio/reader file-obj)]
        (.setRDFHandler pars (chunk-commiter conn))
        (.parse pars fr (.toString (.toURI file-obj)))
        (.commit conn)
        
        ))))

      
      (ltest/with-open-rdf-context cnx RDFFormat/RDFXML "tests/beet.rdf" context-string
        (let [tq1 (.prepareTupleQuery cnx QueryLanguage/SPARQL q1)
              tq2 (.prepareTupleQuery cnx QueryLanguage/SPARQL q2)]
          (try
            (let [r-seq1 (doall (iter-seq (.evaluate tq1)))
                  r-seq2 (doall (iter-seq (.evaluate tq2)))]
              (log/debug "seq1: " r-seq1)
              (log/debug "seq2: " r-seq2)
              (is (?empty r-seq1))
              (is (not (?empty r-seq2)))
              )
            ))
        ))))
