;; file name must follow namespace's name

(ns triple.loader
  (:gen-class)
  (:require [clojure.tools.cli :refer [cli]]
            [clojure.java.io :as io]
            [triple.reifiers :as ref])
  (:import [org.openrdf.repository.http HTTPRepository HTTPRepositoryConnection]
           [org.openrdf.repository RepositoryConnection]
           [org.openrdf.rio RDFFormat ParserConfig]
           [org.openrdf.rio.helpers BasicParserSettings]
           [org.openrdf.query QueryLanguage]
           [org.apache.commons.logging LogFactory]))


(def records-size 1000)

(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/openrdf-sesame"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file N3-formated file" "-f" "N3-formatted data file path"])]
  ;; print help message
  (when (:h opts)
    (println banner)
    (System/exit 0))

  ;; run proper loading
  
  ))


(defn init-connection "Initialise HTTPRepository class to sesame romote repository."
    [server-url repository-id]
  (HTTPRepository. server-url repository-id))



(defn make-parser-config []
    (doto
        (ParserConfig.)
        (.set BasicParserSettings/PRESERVE_BNODE_IDS true))
  )


