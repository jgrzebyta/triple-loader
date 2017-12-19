(ns rdf4j.loader
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [clojure.string :refer [blank?]]
        [clojure.pprint :as pp]
        [rdf4j.reifiers :as ref]
        [rdf4j.version :refer [version]])
  (:require [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [clojure.stacktrace :as cst])
  (:import [java.nio.file Path]
           [java.io File StringWriter]
           [clojure.lang ExceptionInfo]
           [org.eclipse.rdf4j IsolationLevel IsolationLevels]
           [org.eclipse.rdf4j.model Resource Model]
           [org.eclipse.rdf4j.repository.http HTTPRepository HTTPRepositoryConnection]
           [org.eclipse.rdf4j.repository RepositoryConnection RepositoryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings]
           [org.eclipse.rdf4j.query QueryLanguage]
           [org.apache.commons.logging LogFactory]
           [org.eclipse.rdf4j.repository.sail SailRepositoryConnection]
           [org.eclipse.rdf4j.repository.sail.helpers RDFSailInserter]
           [org.eclipse.rdf4j.repository.util RDFInserter]))

(declare load-data load-multidata)

(defn make-parser-config []
    (doto
        (ParserConfig.)
      (.set BasicParserSettings/PRESERVE_BNODE_IDS true)))


(defn init-connection "Initialise Connection." [^RepositoryConnection connection]
  (log/trace "connection instance: " connection)
  (let [repository (.getRepository connection)]
    (log/debug "Repository: " (if (instance? HTTPRepository repository)
                                (.getRepositoryURL repository)
                                (.toString repository)))
    (try
      (.setParserConfig connection (make-parser-config))
      (catch RepositoryException e (do
                                     (log/error (format "Error message: %s" (.getMessage e)))
                                     (throw e)))
      (catch Error t (do
                       (log/error "Error: " (.getMessage t))
                       (System/exit -1))))))

(defn- do-loading [opts]
  ;; validate options
  (assert (and (some? (:s opts)) (some? (:r opts))) "Either server or repository is not given")
  (assert (and (not (blank? (:s opts))) (not (blank? (:r opts)))) "Either server or repository is not given")
  (log/info (format "Triple store server: [%s] repository name: [%s]" (:s opts) (:r opts)))
  (let [repository (HTTPRepository. (:s opts) (:r opts))
        context-string ((fn [x] (if (or (= x "nil")                        ; convert "nil" and "null" texts into boolean nil
                                        (= x "null")
                                        (= x ""))
                                  nil x)) (:c opts))]
    (log/debug (format "Context string: '%s' is nil '%s'"
                       context-string (nil? context-string)))
    (try
      (load-multidata repository (:f opts) :rdf-handler ref/counter-commiter :context-uri context-string)
      (catch ExceptionInfo e (let [f (get (ex-data e) :file)]
                               (log/errorf "Error during loading file '%s'" f)
                               (System/exit -1)))
      (finally (.shutDown repository)))
    (log/info (format "Loaded %d statements" (ref/countStatements)))))



(defmulti load-data
  "[repository file] 

   Loads formated `file` (or model) into `repository`. Selects method based on type of `file`.
   Currently supported types are: String (file path), Model, Path and File.
"
  (fn [repository file & {:keys [rdf-handler context-uri] }] (type file)))

(defmethod load-data String [repository file & {:keys [rdf-handler context-uri]}] (load-data repository (u/normalise-path file) :rdf-handler rdf-handler :context-uri context-uri))

(defmethod load-data Iterable [repository model & {:keys [rdf-handler context-uri]}]
  (r/with-open-repository [cnx repository]
    (init-connection cnx)
    (try
      (.begin cnx)
      (.add cnx model (r/context-array context-uri))
      (finally (.commit cnx)))))

(defmethod load-data Path [repository file & {:keys [rdf-handler context-uri]}] (load-data repository (.toFile file) :rdf-handler rdf-handler :context-uri context-uri))

(defmethod load-data File [repository file & {:keys [rdf-handler context-uri]}]
  (let [file-reader (io/reader file)
        parser-format (.get (Rio/getParserFormatForFileName (.getName file)))
        parser (Rio/createParser parser-format)]
    (log/trace (format "File data: %s [exists: %s, readable: %s]"
                       (.getPath file)
                       (.exists file)
                       (.canRead file)))
    (log/trace (format "data type: %s" parser-format))
    (try
      (r/with-open-repository [cnx repository]
        (init-connection cnx)
        (let [rdf-handler-object (if (some? rdf-handler)
                                   (if (fn? rdf-handler)
                                     (apply rdf-handler [cnx context-uri])
                                     (apply (resolve rdf-handler) [cnx context-uri])) nil)]
                                        ; Set up handler only if the handler was given
          (when (some? rdf-handler-object)
            (log/debug "Set up rdf handler: " rdf-handler-object)
            (.setRDFHandler parser rdf-handler-object))
          
          (log/debug (format "RDF handler: %s" rdf-handler-object))
          ;; run parsing
          (.begin cnx) ;; begin transaction
          (log/trace "Isolation level: " (.getIsolationLevel cnx)) ;; this features returns null for SailRepositoryConnection
          (log/debug "is repository active: " (.isActive cnx))
          
          (if (some? rdf-handler-object)
            (do
              (log/debug "Using own parser...")
              (.parse parser file-reader (.toString (.toURI file))))
            (do
              (log/debug "Using .add method")
              (.add cnx file (.toString (.toURI file)) parser-format (into-array Resource (if (some? context-uri)
                                                                                            [(.createIRI (u/value-factory cnx) context-uri)]
                                                                                            nil) ))))
          (.commit cnx)
          (log/debug "finish ...")))
      (catch Exception e
        (log/debugf "Stack trace: \n%s" (with-out-str (cst/print-stack-trace e)))
        (throw (ex-info (format "Erorr '%s' occured when loaded file '%s'" (.getMessage e) (.getCanonicalPath file))
                        {:error e
                         :file (.getAbsolutePath file)
                         :message (format "Erorr '%s' occured when loaded file '%s'" (.getMessage e) (.getCanonicalPath file))}
                        e))))))

(defn load-multidata "Load multiple data files into repository"
  [repository data-col & { :keys [rdf-handler context-uri]}]
  (assert (some? repository) "Repository is null")
  (assert (not (empty? data-col)) "Data collection is empty")
  (let [wrt (StringWriter.)]
    (pp/pprint data-col wrt)
    (log/debug (format "Data collection [%s]: %s" (type data-col) (.toString wrt))))
  (doall (pmap (fn [itm]
                 (log/infof "Load dataset: %s into context: %s" itm context-uri)
                 (load-data repository (if (u/normalise-path-supportsp itm)
                                         (u/normalise-path itm) itm) :rdf-handler rdf-handler :context-uri context-uri)) data-col)))


(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file FILE" "-f" "Data file path" :assoc-fn #'u/multioption->seq]
                               ["--context IRI" "-c" "Context (graph name) of the dataset. Ignored if file format is context aware, e.g. TriG" :default nil]
                               ["--version" "-V" "Display program version" :defult false :flag true])]

    (cond
      (:h opts) (println banner)
      (:V opts) (println "Version: " version)
      :else (do-loading opts))))
