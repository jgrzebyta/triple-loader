(ns rdf4j.sparql
  (:gen-class
   :main true)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as st :exclude [reverse replace]]
            [clojure.tools.cli :refer [parse-opts]]
            [rdf4j.loader :exclude [-main] :as load]
            [clojure.tools.logging :as log]
            [rdf4j.core.rio :refer [map-formats default-pp-writer-config]]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [rdf4j.writer :as w]
            [rdf4j.version :refer [version]])
  (:import [java.nio.file Paths]
           [java.io FileReader StringWriter]
           [java.util Properties]
           [org.eclipse.rdf4j.model Value]
           [org.eclipse.rdf4j.query MalformedQueryException]
           [org.eclipse.rdf4j.query Query QueryResults TupleQueryResult TupleQueryResultHandler GraphQueryResult TupleQuery GraphQuery BooleanQuery]
           [org.eclipse.rdf4j.query.parser ParsedQuery ParsedBooleanQuery ParsedGraphQuery ParsedTupleQuery]
           [org.eclipse.rdf4j.query.parser.sparql SPARQLParser SPARQLParserFactory]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]
           [org.eclipse.rdf4j.rio Rio RDFFormat RDFWriter ParserConfig RDFParseException RDFHandler]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings StatementCollector]
           [org.eclipse.rdf4j.rio.trig TriGWriter]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore]))

(declare process-sparql-query load-sparql)

(def ^:private cli-options [["-h" "--help" "Print this screen"]
                            ["-f" "--file FILE" "Data file path" :parse-fn (fn [x] (Paths/get x (make-array String 0))) :assoc-fn #'u/multioption->seq]
                            ["-q" "--query QUERY" "SPARQL query. Either as path to file or as string."]
                            ["-t" "--format FORMAT" "Format of SPARQL query resut."]
                            ["-r" "--repository SETTINGS" "Local repository settings." :assoc-fn #'u/comma->seq]
                            ["-b" "--bind PROPERTIES" "Accepts set of properties as SPARQL bindings. Given values are parsed to literal." :default "" :parse-fn (fn [b] (doto
                                                                                                                                                                            (Properties.)
                                                                                                                                                                          (.load (u/string->stream b))))]
                            ["-V" "--version" "Display program version"]])


(defn- validate-args [args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)
        parsed (cond-> {}
                 (:help options) (conj {:msg [summary] :ok true})
                 (:version options) (conj {:msg [(str "Version: " version)] :ok true})
                 errors (conj {:msg errors :ok false})
                 (= "help" (:format options)) (conj {:msg [ (w/help) ] :ok true})
                 (or (:file options)
                     (:query options)
                     (:format options)
                     (:repository options)
                     (:bind options)) (into options))]
    parsed))


(defn- prepare-store
  "Prepares instance of store based on the given options:
 - native :: NativeStore located in /tmp/<<random>> location
 - native=path :: NativeStore located in path location
 default produces MemoryStore."
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
  (let [vf (u/value-factory connection)
        ^Query query (.prepareQuery connection sparql-string)
        writer-factory (cond
                         (some? writer-factory-name) (w/get-factory-by-name writer-factory-name)
                         (instance? TupleQuery query) (w/get-factory-by-name "sparql/tsv")
                         (instance? GraphQuery query) (w/get-factory-by-name "turtle"))
        ^RDFWriter writer (if (some? writer-factory) (.getWriter writer-factory System/out) nil)]
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
            (recur (rest ks))))))
    (log/debug "Writer: " writer)
    (log/debug "Bindling: " (.getBindings query))
    ;; validate writer type
    (when (instance? RDFWriter writer) ;; add support for pretty print
      (.setWriterConfig writer default-pp-writer-config))
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
      (.evaluate query)))) ;; If writer is nil than return only QueryResult

(defn load-sparql [^String sparql-res] "Load SPARQL query from file."
  ;;detect if argument is a file
  (cond
    (.exists (io/file sparql-res)) (with-open [r (io/reader (FileReader. (io/file sparql-res)))] ;; retrieve SPARQL from file
                                     (st/join "\n" (doall (line-seq r)))) 
    (not= :unknown (sparql-type sparql-res)) sparql-res ;; it is SPARQL string
    :else (ex-info "unknown SPARQL resources" {:val sparql-res})))


(defn- run [validated]
  (let [{:keys [file query format repository bind]} validated
          repo (prepare-repository repository)
          sparql-processed (load-sparql query)]
      (log/debugf "options: %s" (with-out-str (clojure.pprint/pprint validated)))
      (when-not (empty? file)
        (load/load-multidata repo file))
      (r/with-open-repository [cnx repo]
        (process-sparql-query cnx sparql-processed :writer-factory-name format :binding bind))
      (log/debug "close context")
      (.shutDown repo)
      (r/delete-context)))

(defn ^{:no-doc true} -main
  "Does SPARQL request

FORMATS
Option '-t :list' gives full list of supported formats. By default writers formats are sparql/tsv and trig for tuple query and graph query respectively.

REPOSITORY
List of options:
- lucene :: Memory store with Lucene support
- simple :: Memory store without Lucene support
- native=<PATH> :: Creates native store located at given location"
  [& args]
  (let [validated (validate-args args)]
    (if (:msg validated)
      (println (st/join \newline (:msg validated)))
      (run validated))
    (shutdown-agents)))
