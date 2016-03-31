(ns triple.reifiers
  (:gen-class)
  (:require [clojure.tools.logging :as log])
  (:import [org.openrdf.model ValueFactory]
           [org.openrdf.model.impl ContextStatement]
           [org.openrdf.rio RDFHandler RDFHandlerException]
           [org.openrdf.rio.helpers AbstractRDFHandler]
           [org.openrdf.repository RepositoryConnection RepositoryException Repository]
           [org.openrdf.repository.util RDFInserter]))


(def chunk-size 1000)


  (defn- prepare-value-factory "Prepare ValueFactory instance from connection"
    [conn]
    (.getValueFactory (.getRepository conn)))

  (defn context-statement "Create context statement"
    [connection context-string simple-statement]
    (let [value-factory (prepare-value-factory connection)
          context-uri (.createIRI value-factory context-string)]
      (.createStatement value-factory (.getSubject simple-statement)
                        (.getPredicate simple-statement)
                        (.getObject simple-statement)
                        context-uri)))



  (defn chunk-commiter "Implements RDFHandler. It accepts 2 arguments "
      ([connection] (chunk-commiter connection nil))
      ([connection context-string]
       (let [statement-converter (if (not (nil? context-string))
                                   (partial context-statement connection context-string)
                                   (fn [x] (do x)))
             inserter (RDFInserter. connection)
             cnt (atom 0)
             value-factory (prepare-value-factory connection)]
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
                                          (.handleStatement inserter (statement-converter statement))
                                          (log/debug (format "[%d] --statement --" @cnt))
                                          (log/trace (format "statement instance %s" (type statement)))
                                     
                                          (swap! cnt inc) ;; increase value of cnt by 1 
                                          (if (= 0 (mod @cnt chunk-size))  ;; @cnt gives value of cnt
                                            (try
                                              (log/debug (format "[%d] time to commit!" @cnt))
                                              (.commit connection)
                                              (catch RepositoryException e (RDFHandlerException. e))
                                              ))))))))

;; (defn statement-counter "implement statement counter based on Sesame's manual: 
;;   http://rdf4j.org/doc/4/programming.docbook?view#chapter-rio1" []
;;   (let [counter 0]
;;     (proxy [AbstractRDFHandler] []
;;       (handleStatement [_]
;;         (alter-var-root 'counter inc))
;;       (getCountedStatements []
;;         counter)
;;       )))
