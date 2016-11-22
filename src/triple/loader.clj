(ns triple.loader
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [clojure.pprint :as pp]
        [triple.reifiers :as ref]
        [triple.version :refer [get-version]]
        [triple.repository])
  (:import [java.nio.file Paths Path]
           [java.io File StringWriter]
           [org.eclipse.rdf4j IsolationLevel IsolationLevels]
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

(declare load-data load-multidata)

(defn make-parser-config []
    (doto
        (ParserConfig.)
      (.set BasicParserSettings/PRESERVE_BNODE_IDS true)))

(defmulti normalise-path "Proceeds path string normalisation. Additionally replace '~' character by Java's 'user.home' system property content."
  (fn [path] (type path)))

(defmethod normalise-path String [path]
  (normalise-path (Paths/get path (make-array String 0))))

(defmethod normalise-path Path [path]
  (let [path-as-string (.toString path)]
    (.normalize (Paths/get (.replaceFirst path-as-string "^~" (System/getProperty "user.home"))
                           (make-array String 0)))))


(defn init-connection "Initialise Connection."
  [^RepositoryConnection connection]
  (log/trace "connection instance: " connection)
  (let [repository (.getRepository connection)]
    (log/debug "Repository: " (if (instance? HTTPRepository repository)
                                (.getRepositoryURL repository)
                                (.getAbsolutePath (.getDataDir repository)))))
  (try
    (.setParserConfig connection (make-parser-config))
    (catch RepositoryException e (do
                                   (log/error (format "Error message: %s" (.getMessage e)))
                                   (throw e)))
    (catch Throwable t (do
                         (log/error "Error: " (.getMessage t))
                         (System/exit -1)))))

(defn- do-loading [opts]
  (let [repository (HTTPRepository. (:s opts) (:r opts))
        context-string ((fn [x] (if (or (= x "nil")                        ; convert "nil" and "null" texts into boolean nil
                                        (= x "null"))
                                  nil x)) (:c opts))]
    (log/debug (format "Context string: '%s' is nil '%s'"
                       context-string (nil? context-string)))
    (try
      (load-multidata repository (:f opts) :rdf-handler ref/chunk-commiter)
      (finally (.shutDown repository)))))



(defmulti load-data "Load formated file into repository."
  (fn [repository file & {:keys [rdf-handler context-uri] }] (type file)))

(defmethod load-data String [repository file & {:keys [rdf-handler context-uri]}] (load-data repository (normalise-path file) :rdf-handler rdf-handler :context-uri context-uri))

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

        ;; run parsing
        (try
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
                                                                                            [(.createIRI (value-factory cnx) context-uri)]
                                                                                            nil) ))
              ;(.parse parser file-reader (.toString (.toURI file-obj)))
              ))
          
          ;;(.parse parser file-reader (.toString (.toURI file-obj))) ;; that works
 ;;         (.add cnx file-obj (.toString (.toURI file-obj)) file-type (context-array))
                    
          (finally (do
                     (log/debug "finish...")
                     (.commit cnx))))
        ))))

(defn load-multidata "Load multiple data files into repository"
  ([repository data-col & { :keys [rdf-handler context-uri]}]
   (assert (some? repository) "Repository is null")
   (assert (not (empty? data-col)) "Data collection is empty")
   (let [wrt (StringWriter.)]
     (pp/pprint data-col wrt)
     (log/debug (format "Data collection [%s]: %s" (type data-col) (.toString wrt))))
   (loop [itms data-col]
     (let [itm (first itms)]
       (when itm
         (do
           (log/info (format "Load record: %s" itm))
           (load-data repository (normalise-path itm) rdf-handler context-uri)
           (recur (rest itms))))))))


(defn multioption->seq "Function handles multioptions for command line arguments"
  [previous key val]
  (assoc previous key
         (if-let [oldval (get previous key)]
           (merge oldval val)
           (list val))))



(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file FILE" "-f" "Data file path" :assoc-fn #'multioption->seq]
                               ["--context IRI" "-c" "Context (graph name) of the dataset" :default nil]
                               ["--version" "-V" "Display program version" :defult false :flag true])]

    (cond
      (:h opts) (println banner)
      (:V opts) (println "Version: " (get-version))
      :else (do-loading opts))))
