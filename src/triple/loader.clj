(ns triple.loader
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
            [clojure.tools.logging :as log]
            [clojure.java.io :as jio]
            [triple.reifiers :as ref]
            [triple.repository])
  (:import [org.eclipse.rdf4j.repository.http HTTPRepository HTTPRepositoryConnection]
           [org.eclipse.rdf4j.repository RepositoryConnection RepositoryException]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.eclipse.rdf4j.rio.helpers BasicParserSettings]
           [org.eclipse.rdf4j.query QueryLanguage]
           [org.apache.commons.logging LogFactory]))


(defn make-parser-config []
    (doto
        (ParserConfig.)
        (.set BasicParserSettings/PRESERVE_BNODE_IDS true)))

(defn decode-format [^String format]
  (case format
    "n3" RDFFormat/N3
    "nq" RDFFormat/NQUADS
    "rdfxml" RDFFormat/RDFXML
    "rdfa" RDFFormat/RDFA
    "turtle" RDFFormat/TURTLE
    RDFFormat/TURTLE))

  (defn init-connection "Initialise Repository."
    ([^RepositoryConnection connection] (init-connection connection false))
    ([^RepositoryConnection connection ^Boolean auto-commit]
     (let [repository (.getRepository connection)]
       (log/debug "Repository: " (if (instance? HTTPRepository repository)
                                   (.getRepositoryURL repository)
                                   (.getAbsolutePath (.getDataDir repository)))))
     (try
       (.setParserConfig connection (make-parser-config))
       (.setAutoCommit connection auto-commit)
       (catch RepositoryException e (do
                                      (log/error (format "Error message: %s" (.getMessage e)))
                                      (throw e)))
       (catch Throwable t (do
                            (log/error "Error: " (.getMessage t))
                            (System/exit 1))))))


(defn- do-loading [opts]
  (let [type (decode-format (:t opts))
        parser (Rio/createParser type)
        file-obj (jio/file (:f opts))
        repository (HTTPRepository. (:s opts) (:r opts))
        context-string ((fn [x] (if (or (= x "nil")                        ; convert "nil" and "null" texts into boolean nil
                                        (= x "null"))
                                  nil x)) (:c opts))]
    (log/debug (format "Context string: '%s' is nil '%s'"
                       context-string (nil? context-string)))
    (with-open-repository (cnx repository)
      (init-connection cnx)
      (.setRDFHandler parser (ref/chunk-commiter cnx context-string))
      (with-open [reader-file (jio/reader file-obj)]
        ; Run parser
        (try
          (.parse parser reader-file (.toString (.toURI file-obj)))
          (catch RDFParseException e
            #(log/error "Error: % for URI: %" (.getMessage e)
                                              (.toString (.toURI file-obj))))
          (catch Throwable t #(do
                                (log/error "The other error caused by " (.getMessage t))
                                (.printStackTrace t)
                                (System/exit -1)))))
      ;; finish transaction
      (log/debug "isActive: " (.isActive cnx))
      (when (.isActive cnx)
        (.commit cnx))
      )))

(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file FILE" "-f" "Data file path"]
                               ["--file-type TYPE" "-t" "Data file type. One of: turtle, n3, nq, rdfxml, rdfa" :default "turtle"]
                               ["--context IRI" "-c" "Context (graph name) of the dataset" :default nil])]
  ;; print help message
  (when (:h opts)
    (println banner)
    (System/exit 0))

  ;; run proper loading
  (do-loading opts)))


