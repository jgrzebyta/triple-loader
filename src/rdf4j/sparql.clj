(ns rdf4j.sparql
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.pprint :as pp]
        [clojure.java.io :as io]
        [clojure.string :as st :exclude [reverse replace]]
        [rdf4j.repository]
        [rdf4j.reifiers]
        [rdf4j.loader :exclude [-main]]
        [rdf4j.version :exclude [-main] :as v])
  (:import [java.io FileReader StringWriter]
           [org.eclipse.rdf4j.query MalformedQueryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat RDFWriter ParserConfig RDFParseException]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings StatementCollector]
           [org.eclipse.rdf4j.query.parser.sparql SPARQLParser SPARQLParserFactory]
           [org.eclipse.rdf4j.rio.turtle TurtleWriter]
           [org.eclipse.rdf4j.query.parser ParsedQuery ParsedBooleanQuery ParsedGraphQuery ParsedTupleQuery]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]
           [org.eclipse.rdf4j.query QueryResults TupleQueryResult GraphQueryResult TupleQuery GraphQuery BooleanQuery]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]))

(declare process-sparql-query load-sparql)



(defn -main [& args]
  "Does SPARQL request"
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--file FILE" "-f" "Data file path" :assoc-fn #'multioption->seq]
                                ["--query" "-q" "SPARQL query. Either as path to file or as string."]
                                ["--version" "-V" "Display program version" :default false :flag true])]
    (cond
      (:h opts) (do (println banner)
                       (System/exit 0))
      (:V opts) (do (println "Version: " (v/get-version))
                          (System/exit 0))
      :else (let [repository (make-repository-with-lucene)
                  sparql (load-sparql (:q opts))
                  dataset (:f opts)]
              (let [wrt (StringWriter. 100)]
                (pprint dataset wrt)
                (log/trace "Request: " (.toString wrt)))
              (load-multidata repository dataset)
              (with-open-repository [cx repository]
                (process-sparql-query cx sparql))
              (.shutDown repository)
              (delete-temp-repository)))))

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


(defn process-sparql-query "Execute SPARQL query through connection. 
If :writer parameter is nil than load results to relevant QueryResultWriter.
If the parameter is :none than returns TupleQueryResult;
otherwise evaluates query with method (.evaluate query writer) with given writer."
  [^RepositoryConnection connection sparql-string & {:keys [writer]}]
  (log/trace (format "SPRQL query: \n%s" sparql-string))
  (try
    (let [query (.prepareQuery connection sparql-string)
          writer (cond
                   (= writer :none) :none
                   (some? writer) writer
                   (instance? TupleQuery query) (SPARQLResultsCSVWriter. System/out)
                   (instance? GraphQuery query) (TurtleWriter. System/out))]
    (log/debug "Writer: " writer)
    (if (= writer :none)
      (.evaluate query)
      (if (instance? BooleanQuery query)
        (print (.evaluate query))
        (.evaluate query writer))))
    (catch Throwable t (do
                          (.rollback connection)
                          (throw t)))
    (finally (.commit connection))))

(defn load-sparql [^String sparql-res] "Load SPARQL query from file."
  ;;detect if argument is a file
  (cond
    (.exists (io/file sparql-res)) (with-open [r (io/reader (FileReader. (io/file sparql-res)))] ;; retrieve SPARQL from file
                                     (st/join "\n" (doall (line-seq r)))) 
    (not= :unknown (sparql-type sparql-res)) sparql-res ;; it is SPARQL string
    :else (ex-info "unknown SPARQL resources" {:val sparql-res})))
