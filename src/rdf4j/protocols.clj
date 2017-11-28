(ns rdf4j.protocols
  (:require [clojure.string :as str])
  (:import [org.eclipse.rdf4j.model Model IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModel LinkedHashModel$ModelStatement]
           [org.eclipse.rdf4j.sail.memory.model MemIRI]))

;; Separate record for holding a graph represented by root node:
;; `root-node` and the `model`.
(defrecord ModelHolder [^IRI root-node ^Model model])

(defmethod print-dup MemIRI [i wr]
  (print-ctor i (fn [o w] (print-dup (.stringValue o) w)) wr))

(defmethod print-dup LinkedHashModel$ModelStatement [i wr]
  (print-ctor i (fn [o w]
                  (print-dup (.toString o) w)) wr))

