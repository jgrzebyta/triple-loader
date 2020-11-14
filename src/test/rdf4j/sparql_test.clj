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
  (:require [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [rdf4j.repository.sails :as sail]
            [rdf4j.core :as c]
            [rdf4j.repository :as repo]
            [clojure.java.io :as io])
  (:import [org.eclipse.rdf4j.rio RDFFormat]
           [clojure.lang ArraySeq]
           [java.io StringWriter ByteArrayOutputStream]
           [org.eclipse.rdf4j.sail.spin SpinSail]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore]
           [org.eclipse.rdf4j.sail.inferencer.fc DedupingInferencer ForwardChainingRDFSInferencer]
           [org.eclipse.rdf4j.lucene.spin LuceneSpinSail]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]))


(deftest args-parsing-test
  (let [args (->
              ["-f" "yeast_rdf" "-t" "turtle" "-q" "join_yeastract_sparql" "-f" "gene_rdf" "-t" "turtle"]
              (to-array)
              (ArraySeq/create))
        options [["-h" "--help" "Print this screen" :default false]
                 ["-f" "--file FILE" "Data file path" :assoc-fn u/multioption->seq ]
                 ["-t" "--file-type TYPE" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa" :assoc-fn u/multioption->seq ]
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
  (let [repo (make-repository-with-lucene)
        input-file-url "resources/beet.rdf"
        input-file (io/file (io/resource input-file-url))]
    (c/load-data repo input-file)
  (testing "Count number of triples in repository"
    (test-repository repo 68))
  (delete-context)))

(deftest test-sparql-query
  (let [repo (make-repository-with-lucene)
        vf (u/value-factory)
        data-file "resources/beet.rdf"]
    (c/load-data repo (io/file (io/resource data-file)))
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
    (let [file-path (.getPath (io/resource "./resources/example1.sparql"))
          sparql-processed (load-sparql file-path)]
      (log/debug (format "SPAQRL processed:\n==========\n %s \n=========\n" sparql-processed))
      (is (< 10 (count sparql-processed)))
      (is (string? sparql-processed) "processed sparql not string")
      (is (not (st/blank? sparql-processed)) "processed sparql is blank")))
  (testing "wrong sparql string"
    (let [sparql-string "seect * where {?s ?p ?o}"]
      (is :unknown (load-sparql sparql-string))))
  (testing "sparql from non existing file"
    (let [sparql-file "nonexists.sparql"]
      (is :unknown (load-sparql sparql-file)))))


(def +local-test+ [
                   (.getPath (io/resource "./resources/yeastract_raw.ttl")),
                   (.getPath (io/resource "./resources/Uniprot_scer.rdf"))
                   ])


(deftest test-search-sparql "Test search sparql "
  (testing "try simple search"
    (let [sparql1 (load-sparql (.getPath (io/resource "resources/spql1.sparql")))
          repo (make-repository-with-lucene)
          loaded-tris (load-multidata repo +local-test+)] ;;load-dataset
      (log/debugf "loaded %d statements" loaded-tris)
      (with-open-repository [c repo]
        (let [response (process-sparql-query c sparql1 :writer-factory-name :none)
              cnt (count (u/iter-seq response))]
          (is (some? response))
          (is (> cnt 0))
          (log/debug (format "triples number: %d" cnt))))
      (delete-context))))


(deftest test-eclipse-rdf4j-220
  (testing "test for eclipse/rdf4j#220"
    (log/info "Memory store")
    (let [repo (make-repository-with-lucene)
          sparql220 (load-sparql (.getPath (io/resource "./resources/issue220.sparql")))
          loaded (load-multidata repo +local-test+)]
      (log/debugf "\n======\n%s\n" sparql220)
      (log/debugf "loaded %d statements" loaded)
      (with-open-repository [c repo]
        ;;(process-sparql-query c sparql220 :writer :none)
        (let [response (u/iter-seq (process-sparql-query c sparql220 :writer-factory-name :none))]
          (is (> (count response) 0))
          (log/info (format "Response size: %d" (count response))))))
    (delete-context))
  (testing "test for eclipse/rdf4j#220 with NativeRepository"
    (log/info "Native store")
    (let [data-dir (.toFile (u/temp-dir))
          repo (sail/make-sail-repository :memory-spin-lucene  data-dir)
          sparql220 (load-sparql (.getPath (io/resource "./resources/issue220.sparql")))
          loaded (load-multidata repo +local-test+)]
      (log/debugf "Data dir: %s" data-dir)
      (log/debugf "\n======\n%s\n" sparql220)
      (log/debugf "loaded %d statements" loaded)
      (with-open-repository [c repo]
        ;;(process-sparql-query c sparql220 :writer :none)
        (let [response (u/iter-seq (process-sparql-query c sparql220 :writer-factory-name :none))]
          (is (> (count response) 0))
          (log/info (format "Response size: %d" (count response)))))
      (delete-context data-dir))))

(deftest test-repository-factory
  (testing "empty options"
    (let [repo (prepare-repository '())]
      (log/debug "repository: " repo)
      (is (instance? SailRepository repo))
      (is (instance? MemoryStore (-> repo
                                     (.getSail)
                                     (.getBaseSail))))
      (log/debugf "store: %s" (.getDataDir repo))
      (delete-context)))
  (testing "test SailRepository + MemoryStore repository initialisation"
    (let [args (list "simple")
          repo (prepare-repository args)]
      (is (instance? SailRepository repo))
      (is (instance? MemoryStore (-> repo
                                     (.getSail)
                                     (.getBaseSail))))
      (delete-context)))
  (testing "test SailRepository + NativeStore with path"
    (let [args (list "simple" "native=/tmp/rdf4j-repository")
          repo (prepare-repository args)]
      (is (instance? SailRepository repo))
      (when-let [store (-> repo
                         (.getSail)
                         (.getBaseSail))]
        (is (instance? NativeStore store))
        (log/debug (format "storage: %s" store))
        (is (= "/tmp/rdf4j-repository" (-> store
                                           (.getDataDir)
                                           (.getAbsolutePath)))))
      (delete-context)))
  (testing "test SailRepository + NativeStore"
    (let [args (list "simple" "native")
          repo (prepare-repository args)]
      (is (instance? SailRepository repo))
      (when-let [store (-> repo
                         (.getSail)
                         (.getBaseSail))]
        (is (instance? NativeStore store))
        (log/debug (format "storage: %s" store)))
      (delete-context)))
  (testing "lucene index + memory"
    (let [args (list "lucene")
          repo (prepare-repository args)]
      (is (instance? LuceneSpinSail (.getSail repo)))
      (delete-context))))

(deftest test-magic-properties
  (let [repo (make-repository-with-lucene)]
    (with-open-repository [cnx repo]
      (testing "spif:split"
        (let [sparql "prefix spif: <http://spinrdf.org/spif#>
                      select ?x where {
                      ?x spif:split ('Very|sour|berry' '\\\\|')
                     } 
                     "
              sparql-proc (load-sparql sparql)
              resp (u/iter-seq (process-sparql-query cnx sparql-proc :writer-factory-name :none))]
          (is (= 3 (count resp)))))
      (testing "apf:strSplit"
        (let [sparql "prefix apf: <http://jena.hpl.hp.com/ARQ/property#>
                      select ?text where {
                      ?text apf:strSplit (\"Very|sour|berry\" \"\\\\|\")
                      }
                     "
              sparql-proc (load-sparql sparql)
              resp (u/iter-seq (process-sparql-query cnx sparql-proc :writer-factory-name :none))]
          (is (= 3 (count resp)))
          )))
    (delete-context)))
