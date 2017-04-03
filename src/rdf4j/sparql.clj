(ns rdf4j.sparql
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [rdf4j.reifiers]
        [rdf4j.writer :as w]
        [rdf4j.loader :exclude [-main]]
        [rdf4j.version :exclude [-main] :as v])
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [clojure.string :as st :exclude [reverse replace]])
  (:import [java.io FileReader StringWriter]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore]
           [org.eclipse.rdf4j.query MalformedQueryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat RDFWriter ParserConfig RDFParseException RDFHandler]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings StatementCollector]
           [org.eclipse.rdf4j.query.parser.sparql SPARQLParser SPARQLParserFactory]
           [org.eclipse.rdf4j.rio.trig TriGWriter]
           [org.eclipse.rdf4j.query.parser ParsedQuery ParsedBooleanQuery ParsedGraphQuery ParsedTupleQuery]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]
           [org.eclipse.rdf4j.query Query QueryResults TupleQueryResult TupleQueryResultHandler GraphQueryResult TupleQuery GraphQuery BooleanQuery]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]
           [org.eclipse.rdf4j.model Value]))

(declare process-sparql-query load-sparql prepare-repository)



(defn -main [& args]
  "Does SPARQL request

FORMATS
Option '-t :list' gives full list of supported formats. By default writers formats are sparql/tsv and trig for tuple query and graph query respectively.

REPOSITORY
List of options:
- lucene :: Memory store with Lucene support
- simple :: Memory store without Lucene support
- native=<PATH> :: Creates native store located at given location
"
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--file FILE" "-f" "Data file path" :assoc-fn #'multioption->seq]
                                ["--query" "-q" "SPARQL query. Either as path to file or as string."]
                                ["--format" "-t" "Format of SPARQL query resut."]
                                ["--repository" "-r" "Local repository settings." :assoc-fn #'u/comma->seq]
                                ["--version" "-V" "Display program version" :default false :flag true])]
    (cond
      (:h opts) (do (println banner)
                    (System/exit 0))
      (:V opts) (do (println "Version: " (v/get-version))
                    (System/exit 0))
      (= ":list" (:t opts)) (do (w/help)
                                (System/exit 0))
      :else (let [repository (prepare-repository (:r opts))
                  sparql (load-sparql (:q opts))
                  dataset (:f opts)
                  writer-factory-name (:t opts)]
              (let [wrt (StringWriter. 100)]
                (pp/pprint dataset wrt)
                (log/trace "Request: " (.toString wrt)))
              (when-not (empty? dataset)
                  (load-multidata repository dataset))
              (r/with-open-repository [cx repository]
                (process-sparql-query cx sparql :writer-factory-name writer-factory-name))
              (.shutDown repository)
              (r/delete-context)))))

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


(defn process-sparql-query
  "
  binding => { :<key> <value> }; value is type of String.

  Execute SPARQL query through connection. 
  If :writer-factory-name parameter is nil than load results to relevant QueryResultWriter.
  If value of the parameter is :none than returns QueryResult;
  otherwise evaluates query with method (.evaluate query writer) with given writer.
  
  Option :binding accepts hash-map where "
  [^RepositoryConnection connection sparql-string & {:keys [writer-factory-name binding]}]
  (log/trace (format "SPRQL query: \n%s" sparql-string))
  (try
    (let [vf (u/value-factory connection)
          ^Query query (.prepareQuery connection sparql-string)
          writer-factory (cond
                           (some? writer-factory-name) (w/get-factory-by-name writer-factory-name)
                           (instance? TupleQuery query) (w/get-factory-by-name "sparql/tsv")
                           (instance? GraphQuery query) (w/get-factory-by-name "trig"))
          writer (if (some? writer-factory) (.getWriter writer-factory System/out) nil)]
      ;; process query binding
      (when (and (< 0 (count binding)) (map? binding))
        (log/debug (format "process binding: %s" (count binding)))
        (loop [ks (keys binding)]
          (when-let [k (first ks)]
            (let [value (get binding k)                             ;; get binding value from the map
                  value-obj (if (instance? Value value)
                              value                                 ;; if value is instance of org.eclipse.rdf4j.model.Value that take it 
                              (.createLiteral vf value))]           ;; otherwise convert using ValueFactory.
              (log/debug "value type: " (type value))
              (.setBinding query (name k) value-obj)
              (recur (rest ks)))))
        )
      (log/debug "Writer: " writer)
      (log/debug "Bindling: " (.getBindings query))
      ;; validate writer type
      (if (some? writer)
        (do
          (if (instance? GraphQuery query)
            (when (not (instance? RDFHandler writer))
              (throw (ex-info "This writer is not suitable for GRAPH queries" { :is (.getClass writer) :expected RDFHandler}))))
          (if (instance? TupleQuery query)
            (when (not (instance? TupleQueryResultHandler writer))
              (throw (ex-info "This writer is not suitable for TUPLE queries" { :is (.getClass writer) :expected TupleQueryResultHandler}))))
          (if (instance? BooleanQuery query)  ;; process main query evaluation.
            (print (.evaluate query))
            (.evaluate query writer)))
        (.evaluate query))) ;; If writer is nil than return only QueryResult
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



(defn- prepare-store
  "Prepares instance of store based on the given options:
 - native :: NativeStore located in /tmp/<<random>> location
 - native=path :: NativeStore located in path location
 default produces MemoryStore.
"
  [option]
  (if-let [store (some #(when (.contains % "native") %) option)]
    (NativeStore. (if-let [path (second (st/split store #"="))]
                    (io/file path)
                    (.toFile (u/temp-dir "sparql"))))
    (MemoryStore.)))

(defn prepare-repository
  "Prepare store and repository based on given arguments."
  [option]
  (let [store (prepare-store option)]
    (cond
      (some #(.contains % "simple") option) (r/make-repository store)
      (some #(.contains % "lucene") option) (r/make-repository-with-lucene store)
      :default (r/make-repository store))))
