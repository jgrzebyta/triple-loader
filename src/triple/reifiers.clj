(ns triple.reifiers
  (:gen-class)
  (:require [clojure.tools.logging :as log])
  (:import [org.openrdf.rio RDFHandler RDFHandlerException]
           [org.openrdf.rio.helpers AbstractRDFHandler]
           [org.openrdf.repository RepositoryConnection RepositoryException]
           [org.openrdf.repository.util RDFInserter]))


(def chunk-size 1000)

(defn chunk-commiter "implement RDFHandler"
  [connection]
  (let [inserter (RDFInserter. connection)
        cnt (atom 0)]
    (proxy [AbstractRDFHandler] []
      (startRDF [] (do
                     (log/trace "do startRDF")
                     (.startRDF inserter)))
      (endRDF [] (do
                   (log/trace "do endRDF")
                   (.endRDF inserter)))
      (handleComment [comment] (.handleComment inserter comment))
      (handleNamespace [prefix uri] (.handleNamespace inserter prefix uri))
      (handleStatement [statement] (do
                                     (.handleStatement inserter statement)
                                     (log/trace (format "[%d] --statement --" @cnt))
                                     (swap! cnt inc) ;; increase value of cnt by 1 
                                     (if (= 0 (mod @cnt chunk-size))  ;; @cnt gives value of cnt
                                       (try
                                         (log/debug (format "[%d] time to commit!" @cnt))
                                          (.commit connection)
                                          (catch RepositoryException e (RDFHandlerException. e))
                                         )))))))

(defn statement-counter "implement statement counter based on Sesame's manual: 
  http://rdf4j.org/doc/4/programming.docbook?view#chapter-rio1" []
  (let [counter 0]
    (proxy [AbstractRDFHandler] []
      (handleStatement [_]
        (alter-var-root 'counter inc))
      (getCountedStatements []
        counter)
      )))


