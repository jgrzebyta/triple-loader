(ns rdf4j.collections
  (:require [clojure.tools.logging :as log]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r])
  (:import [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.model Resource Model]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]))


(defn- seq-list [in-seq]
  (let [model (-> (LinkedHashModelFactory.)
                  (.createEmptyModel))
        vf (u/value-factory)
        root-node (.createBNode vf)]
    (.add model root-node RDF/TYPE RDF/LIST (r/context-array 0)) ;; add root bnode
    
    model))

(defn- seq-collection [rdf-type in-seq]
  )


(defn ^Model seq-to-rdf
  "Converts a sequence into instance of `RDF/LIST` or one of `RDFS/CONTAINER`.

For more details see: `https://www.w3.org/TR/rdf-schema/#ch_othervocab`."
  [rdf-type in-seq]
  (cond
    (= rdf-type RDF/LIST) (seq-list in-seq)
    (or (= rdf-type RDF/BAG)
        (= rdf-type RDF/ALT)
        (= rdf-type RDF/SEQ)) (seq-collection rdf-type in-seq)))

(defmulti rdf-to-seq "Create a sequnce from RDF collections structures" (fn [rdf-statements root-resource] (type rdf-statements)))

(defmethod rdf-to-seq Model [rdf-model root-resource]
  )


(defmethod rdf-to-seq RepositoryConnection [rdf-repository root-resource]
  )
