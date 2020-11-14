(ns rdf4j.collections
  (:require [clojure.tools.logging :as log]
            [rdf4j.core :as c]
            [rdf4j.models :as ms]
            [rdf4j.protocols :as p]
            [rdf4j.utils :as u])
  (:import [java.util NoSuchElementException]
           [org.eclipse.rdf4j.model Resource Model ValueFactory IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModelFactory SimpleNamespace]
           [org.eclipse.rdf4j.model.util Models]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.repository RepositoryConnection]
           org.eclipse.rdf4j.repository.sail.SailRepository
           [rdf4j.protocols ModelHolder]))

(declare sumo-contains get-rest get-first)

(def ^{:no-doc true} vf (u/value-factory))

(defn- seq-list [root-node model in-seq]
  (loop [p-node root-node items in-seq]
    (when-let [i (first items)]
      (let [rest-size (count (rest items))
            ch-node (if (> rest-size 0) (.createBNode vf) RDF/NIL)
            i-node (u/any-to-value i)]
        (.add model p-node RDF/FIRST i-node (u/context-array))
        (.add model p-node RDF/REST ch-node (u/context-array))
        (recur ch-node (rest items))))))

(defn- seq-collection [rdf-type root-node model in-seq]
  (.add model root-node RDF/TYPE rdf-type (u/context-array))
  (loop [it 1 items in-seq]
    (when-let [i (first items)]
      (.add model root-node RDF/LI (u/any-to-value i))
      (recur (+ 1 it) (rest items)))))


(defn ^ModelHolder seq->rdf
  "Converts a sequence into instance of `RDF/LIST` or one of `RDFS/CONTAINER`.
   
  The result graph is wrapped within instance of `ModelHolder`. The `root-node` is
  always blank node.

  For mode details about collections is RDF see:
  `https://www.w3.org/TR/rdf-schema/#ch_othervocab`."
  [rdf-type in-seq]
  { :pre [ (coll? in-seq)] }
  (let [model (-> (LinkedHashModelFactory.)
                  (.createEmptyModel))
        b-root (.createBNode vf)]
      (cond
        (= rdf-type RDF/LIST) (seq-list b-root model in-seq)
        (or (= rdf-type RDF/BAG)
            (= rdf-type RDF/ALT)
            (= rdf-type RDF/SEQ)) (seq-collection rdf-type b-root model in-seq))
      (ModelHolder. b-root model)))

(defn ^ModelHolder seq->rdf-list
  "Simplify converter from clojure collection to `RDF/LIST`."
  [in-seq] (seq->rdf RDF/LIST in-seq))

(defn rdf->seq
  "Converts RDF collection with root `root-resource` within `source-model` to Clojure collection. 

  `coll` variable is used as a starting collection which is populated. Source model is either `Model` or `SailRepository`."
  [source-model root-resource coll]
  {:pre [(or (instance? Model source-model) (instance? SailRepository source-model))]}
  (loop [current-root root-resource
         out-coll coll]
    (let [tmp-out-coll (if-let [first-ob (try
                                           (get-first source-model current-root)
                                           (catch NoSuchElementException e nil))]
                         (conj out-coll first-ob))
          rest (get-rest source-model current-root)]
      (if (= rest RDF/NIL)
        tmp-out-coll
        (recur rest tmp-out-coll)))))

;; Declare SUMO namespace

(def sumo-namespace "http://www.adampease.org/OP/SUMO.owl#")

(def sumo-prefix "sumo")

(def sumo-ns (SimpleNamespace. sumo-prefix sumo-namespace))

(def sumo-contains (.createIRI (u/value-factory) sumo-namespace "contains"))

(defn- get-first
  "Returns object from triple: `root` `RDF/FIRST` object."
  [m ^IRI root]
  (c/rdf-filter-object m root RDF/FIRST))

(defn- get-rest
  "Returns object from triple: `root` `RDF/REST` object."
  [m ^IRI root]
  (let [statements (c/get-statements m root RDF/REST nil false (u/context-array))
        statements-as-model (if (instance? Model statements) statements (c/as-model statements))]
    (-> statements-as-model
        Models/object
        (.orElse RDF/NIL))))
