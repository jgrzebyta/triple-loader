(ns triple.loader
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [triple.reifiers :as ref]
        [triple.version :refer [get-version]]
        [triple.repository])
  (:import [org.eclipse.rdf4j IsolationLevel IsolationLevels]
           [org.eclipse.rdf4j.model Resource]
           [org.eclipse.rdf4j.repository.http HTTPRepository HTTPRepositoryConnection]
           [org.eclipse.rdf4j.repository RepositoryConnection RepositoryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings]
           [org.eclipse.rdf4j.query QueryLanguage]
           [org.apache.commons.logging LogFactory]
           [org.eclipse.rdf4j.repository.sail SailRepositoryConnection]
           [org.eclipse.rdf4j.repository.sail.helpers RDFSailInserter]
           [org.eclipse.rdf4j.repository.util RDFInserter]))

(declare load-data)

(defn make-parser-config []
    (doto
        (ParserConfig.)
        (.set BasicParserSettings/PRESERVE_BNODE_IDS true)))

(defn init-connection "Initialise Repository."
  [^RepositoryConnection connection]
  (log/trace "connection instance: " connection)
  (let [repository (.getRepository connection)]
    (log/debug "Repository: " (if (instance? HTTPRepository repository)
                                (.getRepositoryURL repository)
                                (.getAbsolutePath (.getDataDir repository)))))
  (try
    (.setParserConfig connection (make-parser-config))
    ;; (.setAutoCommit connection auto-commit) ;; deprecated
    (catch RepositoryException e (do
                                   (log/error (format "Error message: %s" (.getMessage e)))
                                   (throw e)))
    (catch Throwable t (do
                         (log/error "Error: " (.getMessage t))
                         (System/exit -1)))))

(defn decode-format [^String format]
  (case format
    "n3" RDFFormat/N3
    "nq" RDFFormat/NQUADS
    "rdfxml" RDFFormat/RDFXML
    "rdfa" RDFFormat/RDFA
    "turtle" RDFFormat/TURTLE
    RDFFormat/TURTLE))


(defn- do-loading [opts]
  (let [type (decode-format (:t opts))
        file-obj (io/file (:f opts))
        repository (HTTPRepository. (:s opts) (:r opts))
        context-string ((fn [x] (if (or (= x "nil")                        ; convert "nil" and "null" texts into boolean nil
                                        (= x "null"))
                                  nil x)) (:c opts))]
    (log/debug (format "Context string: '%s' is nil '%s'"
                       context-string (nil? context-string)))
    (load-data repository (:f opts) type :rdf-handler ref/chunk-commiter)))



(defmulti load-data "Load formated file into repository. The data format is one described by decode-format."
  (fn [repository file file-type & {:keys [rdf-handler context-uri] }] (type file-type)))

(defmethod load-data String [repository file file-type & {:keys [rdf-handler context-uri]}] (load-data repository file (decode-format file-type) :rdf-handler rdf-handler))

(defmethod load-data RDFFormat [repository file file-type & {:keys [rdf-handler context-uri]}]
  (let [file-obj (io/file file)
        file-reader (io/reader file-obj)
        parser (Rio/createParser file-type)]
    (log/trace (format "File data: %s [exists: %s, readable: %s]"
                       (.getPath file-obj)
                       (.exists file-obj)
                       (.canRead file-obj)))
    (log/trace (format "data type: %s" file-type))
    (with-open-repository [cnx repository]
      (init-connection cnx)
      (let [rdf-handler-object (if (some? rdf-handler)
                                 (if (fn? rdf-handler)
                                   (apply rdf-handler cnx context-uri)
                                   (apply (resolve rdf-handler) cnx context-uri)) nil)]
        ; Set up handler only if the handler was given
        (when (some? rdf-handler-object)
          (log/debug "Set up rdf handler: " rdf-handler-object)
          (.setRDFHandler parser rdf-handler-object))
        
        (log/debug (format "RDF handler: %s" rdf-handler-object))
        
                                        ; (.setRDFHandler parser (RDFSailInserter. (.getSailConnection cnx) (value-factory repository))) ;; add RDF Handler suitable for sail Repository
;        (.setRDFHandler parser (RDFInserter. cnx))
        (log/debug "Connection installed ... " cnx)
        ;; run parsing
        (try
          (.begin cnx IsolationLevels/READ_COMMITTED) ;; begin transaction
          (log/trace "Isolation level: " (.getIsolationLevel cnx)) ;; this features returns null for SailRepositoryConnection
          (log/debug "is repository active: " (.isActive cnx))
          
          (if (some? rdf-handler-object)
            (do
              (log/debug "Using own parser...")
              (.parse parser file-reader (.toString (.toURI file-obj))))
            (do
              (log/debug "Using .add method")
              (.add cnx file-obj (.toString (.toURI file-obj)) file-type (make-array Resource 0))
              ;(.parse parser file-reader (.toString (.toURI file-obj)))
              ))
          
          ;;(.parse parser file-reader (.toString (.toURI file-obj))) ;; that works
 ;;         (.add cnx file-obj (.toString (.toURI file-obj)) file-type (context-array))
          
          (catch RDFParseException e
            #(log/error "Error: % for URI: %" (.getMessage e)
                        (.toString (.toURI file-obj)))
            (.rollback cnx))
          (catch Throwable t #(do
                                (.rollback cnx)
                                (log/error "The other error caused by " (.getMessage t))
                                (.printStackTrace t)
                                (System/exit -1)))
          (finally (do
                     (log/debug "finish...")
                     (.commit cnx))))
        ))))


(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file FILE" "-f" "Data file path"]
                               ["--file-type TYPE" "-t" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa" :default "turtle"]
                               ["--context IRI" "-c" "Context (graph name) of the dataset" :default nil]
                               ["--version" "-V" "Display program version" :defult false :flag true])]

    (cond
      (:h opts) (println banner)
      (:V opts) (println "Version: " (get-version))
      :else (do-loading opts))))
