(ns triple.reifiers
  (:gen-class)
  (:require [clojure.tools.logging :as log])
  (:import [org.openrdf.rio RDFHandler RDFHandlerException]
           [org.openrdf.repository RepositoryConnection RepositoryException]
           [org.openrdf.repository.util RDFInserter]))


(def chunk-size 1000)

(defn chunk-commiter "implement RDFHandler"
  [connection]
  (let [inserter (RDFInserter. connection)
        count 0]
    (reify RDFHandler
      (startRDF [this] (.startRDF inserter))
      (endRDF [this] (.endRDF inserter))
      (handleComment [this comment] (.handleComment inserter comment))
      (handleNamespace [this prefix uri] (.handleNamespace inserter prefix uri))
      (handleStatement [this statement] (do
                                          (log/debug "[" count  "] insert statement" statement)
                                          (.handleStatement inserter statement)
                                          (inc count)
                                          (if (= 0 (mod count chunk-size))
                                            (try
                                              (.commit connection)
                                              (catch RepositoryException e (RDFHandlerException. e))
                                         )))))))
