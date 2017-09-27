(ns rdf4j.collections
  (:require [clojure.tools.logging :as log]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r])
  (:import [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.model Resource Model ValueFactory IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory SimpleNamespace]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]))


(defn- seq-list [root-node model in-seq]
  (let [^ValueFactory vf (u/value-factory)]
    (.add model root-node RDF/TYPE RDF/LIST (r/context-array 0)) ;; add root bnode
    (loop [p-node root-node items in-seq]
      (when-let [i (first items)]
        (let [ch-node (.createBNode vf)
              i-node (u/any-to-value i)]
          (.add model p-node RDF/FIRST i-node (r/context-array 0))
          (.add model p-node RDF/REST ch-node)
          (recur ch-node (rest items)))))))

(defn- seq-collection [rdf-type root-node model in-seq]
  (let [^ValueFactory vf (u/value-factory)]
    (.add model root-node RDF/TYPE rdf-type (r/context-array 0))
    (loop [it 1 items in-seq]
      (when-let [i (first items)]
        (.add model root-node (rdf-item it) (u/any-to-value i))
        (recur (+ 1 it) (rest items))))))


(defn ^Model seq-to-rdf
  "Converts a sequence into instance of `RDF/LIST` or one of `RDFS/CONTAINER`.
   
   The result graph is wrapped in triple: `root-node` -- <http://www.adampease.org/OP/SUMO.owl#contains> -- <BNode> .
   For mode details about collections is RDF see: `https://www.w3.org/TR/rdf-schema/#ch_othervocab`."
  [rdf-type ^Resource root-node in-seq]
  { :pre [ (seq? in-seq)] }
  (let [model (-> (LinkedHashModelFactory.)
                  (.createEmptyModel))
        ^ValueFactory vf (u/value-factory)
        b-root (.createBNode vf)]
    (.add model root-node sumo-contains b-root) ;; initialize model
      (cond
        (= rdf-type RDF/LIST) (seq-list b-root model in-seq)
        (or (= rdf-type RDF/BAG)
            (= rdf-type RDF/ALT)
            (= rdf-type RDF/SEQ)) (seq-collection rdf-type b-root model in-seq))
      model))

(defmulti rdf-to-seq "Create a sequnce from RDF collections structures" (fn [rdf-statements root-resource] (type rdf-statements)))

(defmethod rdf-to-seq Model [rdf-model root-resource]
  )


(defmethod rdf-to-seq RepositoryConnection [rdf-repository root-resource]
  )



(defn rdf-item
  "Create instances of `rdfs:ContainerMembershipProperty` (e.g. rdf:_1, rdf:_2) for collections `RDFS/CONTAINER`."
  [i]
  {:pre [(integer? i)] }
  (let [vf (u/value-factory)]
    (.createIRI vf RDF/NAMESPACE (str "_" i))))

;; Declare SUMO namespace

(def sumo-namespace "http://www.adampease.org/OP/SUMO.owl#")

(def sumo-prefix "sumo")

(def sumo-ns (SimpleNamespace. sumo-prefix sumo-namespace))

(def sumo-contains (.createIRI (u/value-factory) sumo-namespace "contains"))
