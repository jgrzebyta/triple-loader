(ns rdf4j.sparql
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.pprint :as pp]
        [clojure.java.io :as io]
        [clojure.string :as st :exclude [reverse replace]]
        [rdf4j.repository]
        [rdf4j.reifiers]
        [rdf4j.writer :as w]
        [rdf4j.loader :exclude [-main]]
        [rdf4j.version :exclude [-main] :as v])
  (:import [java.io FileReader StringWriter]
           [org.eclipse.rdf4j.query MalformedQueryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat RDFWriter ParserConfig RDFParseException RDFHandler]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings StatementCollector]
           [org.eclipse.rdf4j.query.parser.sparql SPARQLParser SPARQLParserFactory]
           [org.eclipse.rdf4j.rio.trig TriGWriter]
           [org.eclipse.rdf4j.query.parser ParsedQuery ParsedBooleanQuery ParsedGraphQuery ParsedTupleQuery]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]
           [org.eclipse.rdf4j.query QueryResults TupleQueryResult TupleQueryResultHandler GraphQueryResult TupleQuery GraphQuery BooleanQuery]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]))

(declare process-sparql-query load-sparql)



(defn -main [& args]
  "Does SPARQL request"
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--file FILE" "-f" "Data file path" :assoc-fn #'multioption->seq]
                                ["--query" "-q" "SPARQL query. Either as path to file or as string."]
                                ["--format" "-t" "Format of SPARQL query resut. Option '-t :list' gives full list of supported formats. By default writers formats are sparql/tsv and trig for tuple query and graph query respectively."]
                                ["--version" "-V" "Display program version" :default false :flag true])]
    (cond
      (:h opts) (do (println banner)
                    (System/exit 0))
      (:V opts) (do (println "Version: " (v/get-version))
                    (System/exit 0))
      (= ":list" (:t opts)) (do (w/help)
                                (System/exit 0))
      :else (let [repository (make-repository-with-lucene)
                  sparql (load-sparql (:q opts))
                  dataset (:f opts)
                  writer-factory-name (:t opts)]
              (let [wrt (StringWriter. 100)]
                (pprint dataset wrt)
                (log/trace "Request: " (.toString wrt)))
              (load-multidata repository dataset)
              (with-open-repository [cx repository]
                (process-sparql-query cx sparql :writer-factory-name writer-factory-name))
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
  [^RepositoryConnection connection sparql-string & {:keys [writer-factory-name]}]
  (log/trace (format "SPRQL query: \n%s" sparql-string))
  (try
    (let [query (.prepareQuery connection sparql-string)
          writer-factory (cond
                           (some? writer-factory-name) (w/get-factory-by-name writer-factory-name)
                           (instance? TupleQuery query) (w/get-factory-by-name "sparql/tsv")
                           (instance? GraphQuery query) (w/get-factory-by-name "trig"))
          writer (.getWriter writer-factory System/out)]
      (log/debug "Writer: " writer)
      ;; validate writer type
      (if (instance? GraphQuery query)
        (when (not (instance? RDFHandler writer))
          (throw (ex-info "This writer is not suitable for GRAPH queries" { :is (.getClass writer) :expected RDFHandler}))))
      (if (instance? TupleQuery query)
        (when (not (instance? TupleQueryResultHandler writer))
          (throw (ex-info "This writer is not suitable for TUPLE queries" { :is (.getClass writer) :expected TupleQueryResultHandler}))))
      (if (instance? BooleanQuery query)
        (print (.evaluate query))
        (.evaluate query writer)))
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
