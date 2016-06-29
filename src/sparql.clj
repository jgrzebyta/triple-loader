(ns sparql
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.pprint :as pp]
        [clojure.java.io :as io]
        [clojure.string :as st :exclude [reverse replace]]
        [triple.repository]
        [triple.reifiers]
        [triple.loader :exclude [-main]])
  (:import [java.io FileReader StringWriter]
           [java.util.concurrent Executors]
           [org.eclipse.rdf4j.query MalformedQueryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat RDFWriter ParserConfig RDFParseException]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings StatementCollector]
           [org.eclipse.rdf4j.query.parser.sparql SPARQLParser SPARQLParserFactory]
           [org.eclipse.rdf4j.rio.turtle TurtleWriter]
           [org.eclipse.rdf4j.query.parser ParsedQuery ParsedBooleanQuery ParsedGraphQuery ParsedTupleQuery]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVWriter]
           [org.eclipse.rdf4j.query QueryResults TupleQueryResult GraphQueryResult TupleQuery GraphQuery BooleanQuery]
           [org.eclipse.rdf4j.repository RepositoryResult RepositoryConnection]))

(declare load-multidata process-sparql-query load-sparql)


(defn- multioption->seq "Function handles multioptions for command line arguments"
  [previous key val]
  (assoc previous key
         (if-let [oldval (get previous key)]
           (merge oldval val)
           (hash-set val))))

(defn- map-seqs "Join data files with related type"
  [data-files types]
  ;; check if both sets are not empty
  (while (or (empty? data-files) (empty? types)) (ex-info "Empty arguments") { :causes #{:empty-collection-not-expected}})
  ;; check if lenght of both sets it the same
  (while (not= (count data-files)
               (count types)) (throw
                               (ex-info "Data files and types are not matched" { :causes #{:data-files-and-types-not-matched}
                                                                                :length {:data (count data-files) :types (count types)}})))
  ;; create a colletion of map with keys: :data-file and :type.
  (map (fn [x y] {:data-file x :type y} data-files types)))


(defn -main [& args]
  "Does SPARQL request"
  (let [[opts args banner] (cli args
                                ["-h" "--help" "Print this screen" :default false :flag true]
                                ["-f" "--file" "Data file path. Multiple values accepted." :assoc-fn #'multioption->seq ]
                                ["-t" "--file-type" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa"
                                 :default "turtle"
                                 :assoc-fn #'multioption->seq ]
                                ["-q" "--query" "SPARQL query. Either as path to file or as string."])]
    (when (:help opts)
      (println banner)
      (System/exit 0))
    (let [repository (make-repository-with-lucene)
          sparql (load-sparql (:query opts))
          dataset (map-seqs (:file opts) (:file-type opts))]
      (load-multidata repository dataset)
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


(defn load-multidata "Load multiple data into repository" [repository data-col]
  (assert (some? repository) "Repository is null")
  (assert (not (empty? data-col)) "Data collection is empty")
  (loop [itms data-col]
    (let [itm (first itms)]
      (when itm
        (do
          (log/debug "Load record: " (pp/pprint itm))
          (load-data repository (get itm :data-file) (get itm :type))
          (recur (rest itms)))))))


(defn load-sparql [^String sparql-res] "Load SPARQL query from file."
  ;;detect if argument is a file
  (log/debug "SPRQL query: \"" sparql-res  "\"")
  (cond
    (.exists (io/file sparql-res)) (with-open [r (io/reader (FileReader. (io/file sparql-res)))] ;; retrieve SPARQL from file
                                     (st/join "\n" (doall (line-seq r)))) 
    (not= :unknown (sparql-type sparql-res)) sparql-res ;; it is SPARQL string
    :else (ex-info "unknown SPARQL resources" {:val sparql-res})))
