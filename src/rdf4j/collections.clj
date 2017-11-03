(ns rdf4j.collections
  (:require [clojure.tools.logging :as log]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [rdf4j.triples-source.wrappers :as w]
            [rdf4j.models :as m])
  (:import [java.util NoSuchElementException]
           [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.model Resource Model ValueFactory IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory SimpleNamespace]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.model.util Models]))

(declare sumo-contains get-rest get-first)

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
        (.add model root-node RDF/LI (u/any-to-value i))
        (recur (+ 1 it) (rest items))))))


(defn ^Model seq->rdf
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

(defn rdf->seq
  "Converts RDF collection with root `root-resource` within `source-model` to Clojure collection. 

  `coll` variable is used as a starting collection which is populated."
  [source-model root-resource coll]
  (loop [current-root root-resource
         out-coll coll]
    (let [tmp-out-coll (if-let [first (try
                                        (get-first source-model current-root)
                                        (catch NoSuchElementException e nil))]
                         (conj out-coll first))
          rest (try
                 (get-rest source-model current-root)
                 (catch NoSuchElementException e RDF/NIL))]
      (if (= rest RDF/NIL)
        tmp-out-coll
        (recur rest tmp-out-coll)))))

;; Declare SUMO namespace

(def sumo-namespace "http://www.adampease.org/OP/SUMO.owl#")

(def sumo-prefix "sumo")

(def sumo-ns (SimpleNamespace. sumo-prefix sumo-namespace))

(def sumo-contains (.createIRI (u/value-factory) sumo-namespace "contains"))

(defn- get-first
  [^Model m ^IRI root]
  (->
   (m/rdf-filter m root RDF/FIRST nil)
   (Models/object)
   (.get)))

(defn- get-rest
  "Returns object from triple: `root` `RDF/REST` object."
  [^Model m ^IRI root]
  (->
   (m/rdf-filter m root RDF/REST nil)
   (Models/object)
   (.get)))
