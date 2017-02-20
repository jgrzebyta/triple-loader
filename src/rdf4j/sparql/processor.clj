(ns rdf4j.sparql.processor
  (:require [clojure.tools.logging :as log]
            [rdf4j.repository :as r]
            [rdf4j.sparql :as sp :exclude [-main]]
            [rdf4j.loader :as l :exclude [-main]]))


(defmacro with-sparql [args & body]
  "Process SPARQL request on given resources."
  (let [args (apply hash-map args) ;; converts vector of arguments into map
        query (or (:query args) (:sparql args))
        data-seq (seq (:data args))
        data-loading (if (some? data-seq) `(l/load-multidata repo# ~data-seq) `(log/warn "data not loaded"))
        result-seq (:result args)]
    `(do
       (let [repo# (r/make-repository-with-lucene)
             sparql-processed# (sp/load-sparql ~query)]
         ~data-loading
         (r/with-open-repository [cn# repo#]
           (let [~result-seq (r/iter-seq (sp/process-sparql-query cn# sparql-processed# :writer-factory-name :none))]
             ~@body)))
       (r/delete-context))
    ))
