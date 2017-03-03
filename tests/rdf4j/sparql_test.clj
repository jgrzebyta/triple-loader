(ns rdf4j.sparql-test
  (:use [rdf4j.sparql :exclude [-main]]
        [clojure.tools.cli :refer [cli parse-opts]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.string :as st :exclude [reverse replace]]
        [clojure.pprint :as pp]
        [clojure.test]
        [rdf4j.repository]
        [rdf4j.loader :exclude [-main]]
        [rdf4j.loader-test])
  (:require [rdf4j.utils :as u])
  (:import [org.eclipse.rdf4j.rio RDFFormat]
           [clojure.lang ArraySeq]
           [java.io StringWriter ByteArrayOutputStream]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]))


(deftest args-parsing-test
  (let [args (->
              ["-f" "yeast_rdf" "-t" "turtle" "-q" "join_yeastract_sparql" "-f" "gene_rdf" "-t" "turtle"]
              (to-array)
              (ArraySeq/create))
        options [["-h" "--help" "Print this screen" :default false]
                 ["-f" "--file FILE" "Data file path" :assoc-fn multioption->seq ]
                 ["-t" "--file-type TYPE" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa" :assoc-fn multioption->seq ]
                 ["-q" "--query SPARQL" "SPARQL query. Either as path to file or as string."]
                 ["-V" "--version" "Display program version" :default false]]
        parse-out (parse-opts args options)]
    (testing "parsing outcome"
      (let [wrt (StringWriter.)]
        (pp/pprint parse-out wrt)
        (log/debug (format "structure: \n %s\n" (.toString wrt))))
      (log/debug "Data:" (-> parse-out (get :options) (get :file)))
      (log/debug "Data type:" (-> parse-out (get :options) (get :file-type)))
      (is (= (count (-> parse-out (get :options) (get :file)))
             (count (-> parse-out (get :options) (get :file-type)))) "Data file and file type arrays has different size"))
    ))




(deftest test-load-data 
  (let [repo (make-repository-with-lucene)]
    (load-data repo "tests/beet.rdf")
  (testing "Count number of triples in repository"
    (test-repository repo 68))
  (delete-context)))


(deftest test-sparql-query
  (let [repo (make-repository-with-lucene)
        vf (u/value-factory)]
    (load-data repo "tests/beet.rdf")
    (testing "execute simple SPARQL query"
      (let [sparql-str "select * where {?s ?p ?o}"]
        (with-open-repository [cx repo]
          (println "\n")
          (let [res (process-sparql-query cx sparql-str :writer-factory-name :none)]
            (is (< 0 (count (u/iter-seq res))))
            ))))
    
    (testing "execute lucene SPARQL query"
      (let [binding { :term (u/value-factory)}
            sparql-str "
PREFIX search: <http://www.openrdf.org/contrib/lucenesail#>  
select ?pred ?score ?snip 
where { 
(\"Germany\" search:allMatches search:score) search:search (?pred ?score)}"]
        (with-open-repository [cx repo]
          (let [resp  (process-sparql-query cx sparql-str :writer-factory-name :none)]
            (is (< 0 (count (u/iter-seq resp)))))
          )))
    
    (testing "execute lucene SPARQL query with binding"
      (let [binding { :term "Germany"}
            sparql-str "
PREFIX search: <http://www.openrdf.org/contrib/lucenesail#>  

select 
?pred ?score
where {
(?term search:allMatches search:score) search:search (?pred ?score)
}"]
        (with-open-repository [cx repo]
          (let [resp (u/iter-seq (process-sparql-query cx sparql-str :writer-factory-name :none :binding binding))]
            (is (< 0 (count resp)))
            (log/info "Number: " (count resp))))))
    (delete-context)))

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


(def +local-test+ ["./tests/resources/yeastract_raw.ttl",
                   "./tests/resources/Uniprot_scer.rdf"])


(deftest test-search-sparql "Test search sparql "
  (testing "try simple search"
    (let [sparql1 (load-sparql "tests/resources/spql1.sparql")
          repo (make-repository-with-lucene)]
      ;; load dataset
      (load-multidata repo +local-test+)
      (with-open-repository [c repo]
        (let [response (process-sparql-query c sparql1 :writer-factory-name :none)
              cnt (count (u/iter-seq response))]
          (is (some? response))
          (is (> cnt 0))
          (log/debug (format "triples number: %d" cnt))))
      (delete-context))))


(deftest test-eclipse-rdf4j-220
  (testing "test for issue eclipse/rdf4j#220"
    (let [repo (make-repository-with-lucene)
          sparql220 (load-sparql "tests/resources/issue220.sparql")]
      (log/debug (format "\n======\n%s\n" sparql220))
      (load-multidata repo +local-test+)
      (with-open-repository [c repo]
        ;;(process-sparql-query c sparql220 :writer :none)
        (let [response (u/iter-seq (process-sparql-query c sparql220 :writer-factory-name :none))]
          (is (> (count response) 0))
          (log/info (format "Response size: %d" (count response))))))
    (delete-context)))
