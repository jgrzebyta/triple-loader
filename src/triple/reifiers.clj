(ns triple.reifiers
  (:use [triple.repository]
        [clojure.tools.logging :as log])
  (:import [org.eclipse.rdf4j.model ValueFactory]
           [org.eclipse.rdf4j.model.impl ContextStatement]
           [org.eclipse.rdf4j.rio RDFHandler RDFHandlerException Rio RDFFormat]
           [org.eclipse.rdf4j.rio.helpers AbstractRDFHandler]
           [org.eclipse.rdf4j.repository RepositoryConnection RepositoryException Repository]
           [org.eclipse.rdf4j.repository.util RDFInserter]))


(def chunk-size 1000)


(defn context-statement "Create context statement"
  [connection context-string simple-statement]
  (let [vf (value-factory connection)
        context-uri (.createIRI vf context-string)]
    (log/debug "Value factory instance " (type value-factory))
    (.createStatement vf (.getSubject simple-statement)
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
