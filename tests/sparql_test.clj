(ns sparql-test
  (:gen-class)
  (:use [sparql :exclude [-main]]
        [clojure.tools.cli :refer [cli parse-opts]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.string :as st :exclude [reverse replace]]
        [clojure.pprint :as pp]
        [clojure.test]
        [triple.repository]
        [triple.loader :exclude [-main]]
        [triple.loader-test])
  (:import [org.eclipse.rdf4j.rio RDFFormat]
           [clojure.lang ArraySeq]
           [java.io StringWriter]))


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
  (delete-temp-repository)))


(deftest test-sparql-query
  (let [repo (make-repository-with-lucene)]
    (load-data repo "tests/beet.rdf")
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
        ))
    (delete-temp-repository)))

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
