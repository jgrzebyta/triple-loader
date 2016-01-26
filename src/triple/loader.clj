;; file name must follow namespace's name

(ns triple.loader
  (:gen-class)
  (:require [clojure.tools.cli :refer [cli]]
            [clojure.tools.logging :as log]
            [clojure.java.io :as jio]
            [triple.reifiers :as ref])
  (:import [org.openrdf.repository.http HTTPRepository HTTPRepositoryConnection]
           [org.openrdf.repository RepositoryConnection RepositoryException]
           [org.openrdf.rio Rio RDFFormat ParserConfig RDFParseException]
           [org.openrdf.rio.helpers BasicParserSettings]
           [org.openrdf.query QueryLanguage]
           [org.apache.commons.logging LogFactory]))


(defn make-parser-config []
    (doto
        (ParserConfig.)
        (.set BasicParserSettings/PRESERVE_BNODE_IDS true)))


(defn init-connection "Initialise HTTPRepository class to sesame remote repository."
  [server-url repository-id]
  (let [repo (HTTPRepository. server-url repository-id)]
    (.initialize repo)
    (log/debug "Connection: " (.getRepositoryURL repo))
    (try
      (doto  ;; create connection
        (.getConnection repo)
      (.setParserConfig (make-parser-config))
      (.setAutoCommit false))
      (catch RepositoryException e (do
                                     (log/error (format "Error message: %s" (.getMessage e)))
                                     (throw e)))
      (catch Throwable t (do
                           (log/error "Error: " (.getMessage t))
                           (System/exit 1))))))


(defn do-loading [opts]
  (let [file-obj (jio/file (:f opts))]
    (with-open [c (init-connection (:s opts) (:r opts))
                reader-file (jio/reader file-obj)]
      (let [format (case (:t opts)
                     "n3" RDFFormat/N3
                     "nq" RDFFormat/NQUADS
                     "rdfxml" RDFFormat/RDFXML
                     "rdfa" RDFFormat/RDFA
                     "turtle" RDFFormat/TURTLE
                     RDFFormat/TURTLE)
            parser (doto
                       (Rio/createParser format)
                     (.setRDFHandler (ref/chunk-commiter c)))]
        ;;(println (format "base URI: %s" (.toString (.toURI file-obj))))
        (log/debug "start file parsing")
        (try
          (.parse parser reader-file (.toString (.toURI file-obj)))
          (catch RDFParseException e
            #(log/error "Error: % for URI: %" (.getMessage e)
                                              (.toString (.toURI file-obj))))
          (catch Throwable t #(do
                                (log/error "The other error caused by " (.getMessage t))
                                (.printStackTrace t)
                                (System/exit -1))))
          ;; finish transaction
        (log/debug "isActive: " (.isActive c))
        (when (.isActive c)
          (.commit c))
          ))))


(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/openrdf-sesame"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file FILE" "-f" "Data file path"]
                               ["--file-type TYPE" "-t" "Data file type. One of: n3, nq, rdfxml, rdfa" :default "turtle"])]
  ;; print help message
  (when (:h opts)
    (println banner)
    (System/exit 0))

  ;; run proper loading
  (do-loading opts)))


