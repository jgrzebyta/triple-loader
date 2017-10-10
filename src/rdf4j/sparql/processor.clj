(ns rdf4j.sparql.processor
  (:require [clojure.tools.logging :as log]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [rdf4j.sparql :as sp :exclude [-main]]
            [rdf4j.loader :as l :exclude [-main]])
  (:import [java.util Collection]))


(defn- make-repository-bind
  "Prepares repository fragment for macro with-sparql"
  [& [repository]]
  (if-let [repo repository]
      [`~repo nil]
      [`(r/make-repository-with-lucene) `((r/delete-context))]))

(defmacro with-sparql
  " args => [key value ...]
  Evaluates body in context of processed SPARQL request on given data.
  
  The query (`:query`) result is exposed to the body with variable defined by key `:result` and
  is a sequence of BindingSets or Statements for tuple or graph queries respectively. 
  
  All possible keys are: 
     - :query or :sparql (required) :: SPARQL query as string
     - :result (required) :: Declares variable for holding sparql query results
     - :data (optional) :: A collection of data loaded into repository
     - :binding (optional) :: A map with bindings
     - :repository (optional). :: User's repository. Alternatively creates one using `(r/make-repository-with-lucene)`"
  {:added "0.1.15"}
  [args & body]
  (let [args-map (apply hash-map args) ;; converts vector of arguments into map
        query (or (:query args-map) (:sparql args-map))
        in-data (:data args-map)
        data-seq (gensym "data_")
        result-seq (:result args-map)
        binding (:binding args-map)
        binding-seq (if binding `(:binding ~binding))
        [repo-start repo-end] (make-repository-bind (:repository args-map))]
    `(let [repo# ~repo-start
           ~data-seq ~in-data
           sparql-processed# (sp/load-sparql ~query)]
       (when ~data-seq (l/load-multidata repo# ~data-seq))
       (r/with-open-repository [cn# repo#]
         (let [~result-seq (u/iter-seq
                            (sp/process-sparql-query cn# sparql-processed#
                                                     :writer-factory-name :none ~@binding-seq))]
           ~@body))
       ~@(when-let [del repo-end]
           del))))

