(ns sparql
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

(declare load-data process-sparql-query load-sparql)


(defn -main [& args]
  "Does SPARQL request"
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--file FILE" "-f" "Data file path"]
                                ["--file-type TYPE" "-t" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa" :default "turtle"]
                                ["--query" "-q" "SPARQL query. Either as path to file or as string."])]
    (when (:h opts)
      (println banner)
      (System/exit 0))
      (let [repository (make-repository-with-lucene)
            sparql (load-sparql (:q opts))]
        (load-data repository (:f opts) (:t opts))
        (with-open-repository [cx repository]
          (process-sparql-query cx sparql))
        (delete-temp-repository))))

(defn sparql-type "Returns a type of given SPARQL query. There are three type of queries: :tuple, :graph and :boolean"
  [^String sparql]
  (let [parserFactory (SPARQLParserFactory.)
        parser (.getParser parserFactory)
        parsedQuery (.parseQuery parser sparql nil)]
    (cond
      (instance? ParsedTupleQuery parsedQuery) :tuple
      (instance? ParsedBooleanQuery parsedQuery) :boolean
      (instance? ParsedGraphQuery parsedQuery) :graph
      :else :unknown)))


(defn process-sparql-query "Execute SPARQL query through connection."
  [^RepositoryConnection connection sparql-string]
  (let [query (.prepareQuery connection sparql-string)
        writer (cond
                 (instance? TupleQuery query) (SPARQLResultsCSVWriter. System/out)
                 (instance? GraphQuery query) (TurtleWriter. System/out))]
    (if (instance? BooleanQuery query)
      (print (.evaluate query))
      (.evaluate query writer))))


(defmulti load-data "Load formated file into repository. The data format is one described by decode-format."
  (fn [repository file file-type] (type file-type)))

(defmethod load-data String [repository file file-type] (load-data repository file (decode-format file-type)))

(defmethod load-data RDFFormat [repository file file-type] 
  (let [file-obj (io/file file)
        file-reader (io/reader file-obj)
        parser (Rio/createParser file-type)]
    (with-open-repository (cnx repository)
      (init-connection cnx true)
      (log/debug "is repository autocomit: " (.isAutoCommit cnx))
      (.setRDFHandler parser (RDFSailInserter. (.getSailConnection cnx) (value-factory repository))) ;; add RDF Handler suitable for sail Repository
      ;; run parsing
      (try
        (.begin cnx) ;; begin transaction
        (.parse parser file-reader (.toString (.toURI file-obj)))
        (catch RDFParseException e
          #(log/error "Error: % for URI: %" (.getMessage e)
                      (.toString (.toURI file-obj))))
        (catch Throwable t #(do
                              ()
                              (log/error "The other error caused by " (.getMessage t))
                              (.printStackTrace t)
                              (System/exit -1)))
        (finally (.commit cnx))))))


(defn load-sparql [^String sparql-res] "Load SPARQL query from file."
  ;;detect if argument is a file
  (cond
    (.exists (io/file sparql-res)) (with-open [r (io/reader (FileReader. (io/file sparql-res)))] ;; retrieve SPARQL from file
                                     (st/join "\n" (doall (line-seq r)))) 
    (not= :unknown (sparql-type sparql-res)) sparql-res ;; it is SPARQL string
    :else (ex-info "unknown SPARQL resources" {:val sparql-res})))