(ns rdf4j.sparql.processor
  (:require [clojure.tools.logging :as log]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [rdf4j.sparql :as sp :exclude [-main]]
            [rdf4j.loader :as l :exclude [-main]])
  (:import [java.util Collection]))



(defmacro with-sparql
  " args => [key value ...]

  Evaluates body in context of processed SPARQL request on given data.
  The query result is exposed to the body with variable defined by key :result and
  is a sequence of BindingSets or Statements for tuple or graph queries respectively. 
  Possible keys are: :query or :sparql (required), :result (required), :data (optional) and :binding(optional)."
  {:added "0.1.15"}
  [args & body]
  (let [args-map (apply hash-map args) ;; converts vector of arguments into map
        query (or (:query args-map) (:sparql args-map))
        data-seq (seq (:data args-map))
        result-seq (:result args-map)
        binding (:binding args-map)
        binding-seq (if binding `(:binding ~binding))]
    (log/debug (format "Arguments: %s [%s]" args-map (type args-map)))
    `(do
       (let [repo# (r/make-repository-with-lucene)
             sparql-processed# (sp/load-sparql ~query)]
         (if (some? ~data-seq) (l/load-multidata repo# ~data-seq) (log/warn "data not loaded"))
         (r/with-open-repository [cn# repo#]
           (let [~result-seq (u/iter-seq
                              (sp/process-sparql-query cn# sparql-processed#
                                                       :writer-factory-name :none ~@binding-seq))]
             ~@body)))
       (r/delete-context))))
