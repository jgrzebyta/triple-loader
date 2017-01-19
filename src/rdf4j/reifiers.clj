(ns rdf4j.reifiers
  (:use [rdf4j.repository]
        [clojure.tools.logging :as log])
  (:import [org.eclipse.rdf4j.model ValueFactory]
           [org.eclipse.rdf4j.model.impl ContextStatement]
           [org.eclipse.rdf4j.rio RDFHandler RDFHandlerException Rio RDFFormat]
           [org.eclipse.rdf4j.rio.helpers AbstractRDFHandler]
           [org.eclipse.rdf4j.repository RepositoryConnection RepositoryException Repository]
           [org.eclipse.rdf4j.repository.util RDFInserter]))


(defn context-statement "Create context statement"
  [connection context-string simple-statement]
  (let [vf (value-factory connection)
        context-uri (.createIRI vf context-string)]
    (log/debug "Value factory instance " (type value-factory))
    (.createStatement vf (.getSubject simple-statement)
                      (.getPredicate simple-statement)
                      (.getObject simple-statement)
                      context-uri)))

;; Chunk loading is implemented internally in HTTPRepositoryConnection 
#_(defn chunk-commiter (nil))

(def cnt (atom 0))

(defn counter-commiter "Implements RDFHandler. It accepts 2 arguments "
  ([connection] (counter-commiter connection nil))
  ([connection context-string]
   (let [statement-converter (if (some? context-string)
                               (partial context-statement connection context-string)
                               (fn [x] (do x)))
         inserter (RDFInserter. connection)
         value-factory (value-factory connection)]
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
                                      ))))))

(defn countStatements [] (deref cnt))
