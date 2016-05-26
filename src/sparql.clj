(ns sparql
  "Does SPARQL request on repository"
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [clojure.string :as st :exclude [reverse replace]]
        [triple.repository]
        [triple.reifiers]
        [triple.loader :exclude [-main]])
  (:import [java.io FileReader]
           [org.eclipse.rdf4j.query MalformedQueryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat RDFWriter ParserConfig RDFParseException]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings StatementCollector]
           [org.eclipse.rdf4j.query.parser.sparql SPARQLParser SPARQLParserFactory]
           [org.eclipse.rdf4j.rio.turtle TurtleWriter]
           [org.eclipse.rdf4j.query.parser ParsedQuery ParsedBooleanQuery ParsedGraphQuery ParsedTupleQuery]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]
           [org.eclipse.rdf4j.query QueryResults TupleQueryResult GraphQueryResult TupleQuery GraphQuery BooleanQuery]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]
           [org.eclipse.rdf4j.repository.sail.helpers RDFSailInserter]))

(declare run-sparql)


(defn -main [& args]
  "Does SPARQL request"
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--file FILE" "-f" "Data file path"]
                                ["--file-type TYPE" "-t" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa" :default "turtle"]
                                ["--query" "-q" "SPARQL query. Either as path to file or as string."]
                                ["--base-uri" "-b" "Base URI. By default it is created from data path."])]
    (when (:h opts)
      (println banner)
      (System/exit 0))
    (run-sparql opts)))




(defn sparql-type
  [^String sparql]
  "Returns a type of given SPARQL query. There are three type of queries: :tuple, :graph and :boolean"
  (let [parserFactory (SPARQLParserFactory.)
        parser (.getParser parserFactory)
        parsedQuery (.parseQuery parser sparql nil)]
    (cond
      (instance? ParsedTupleQuery parsedQuery) :tuple
      (instance? ParsedBooleanQuery parsedQuery) :boolean
      (instance? ParsedGraphQuery parsedQuery) :graph
      :else :unknown)))


(defn process-sparql-query [^RepositoryConnection connection sparql-string]
  (let [query (.prepareQuery connection sparql-string)
        writer (cond
                 (instance? TupleQuery query) (SPARQLResultsCSVWriter. System/out)
                 (instance? GraphQuery query) (TurtleWriter. System/out))]
    (if (instance? BooleanQuery query)
      (print (.evaluate query))
      (.evaluate query writer))))


(defn load-data [repository file file-type] "Load formated file into repository. The data format is one described by decode-format."
  (let [file-obj (io/file file)
        file-reader (io/reader file-obj)
        parser (Rio/createParser file-type)]
    (with-open-repository (cnx repository)
      (init-connection cnx true)
      (log/debug "is repository autocomit: " (.isAutoCommit cnx))
      (.setRDFHandler parser (RDFSailInserter. (.getSailConnection cnx) (value-factory repository))) ;; add RDF Handler suitable for sail Repository
      ;; run parsing
      (try
        (.begin cnx)
        (.parse parser file-reader (.toString (.toURI file-obj)))
        (catch RDFParseException e
          #(log/error "Error: % for URI: %" (.getMessage e)
                      (.toString (.toURI file-obj))))
        (catch Throwable t #(do
                              (log/error "The other error caused by " (.getMessage t))
                              (.printStackTrace t)
                              (System/exit -1)))
        (finally (.commit cnx))))))


(defn load-sparql [sparql-res] "Load SPARQL query from file."
  ;;detect if argument is a file
  (cond
    (.exists (as-file sparql-res)) (with-open [r (io/reader (FileReader. (as-file sparql-res)))] ;; retrieve SPARQL from file
                                     (st/join "\n" (doall (line-seq r)))) 
    (not= :unknown (sparql-type sparql-res)) sparql-res ;; it is SPARQL string
    :else (ex-info "unknown SPARQL resources" {:val sparql-res})))


(defn run-sparql [opts] "Execute SPARQL request"
  ;; load data
  (let [repository (make-repository-with-lucene)]
    (load-data repository (:f opts) (decode-format (:t opts)))
    (with-open-repository [cx repository]
      (process-sparql-query cx (load-sparql (:q opts))))
    ))
