(ns rdf4j.triples-source.wrappers
  (:require [rdf4j.repository :as r])
  (:import [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.model Model Resource IRI Value]))


(defprotocol GenericTriplesSourceProtocol

  "This protocol describes generic methods for triples retrieving. The implementations of the protocol
   wraps either `Model` or `ReppositoryConnection`. The user is `root-source` agnostic - does not have
   to know the API. 

   `get-triples` methods are defined with arguments pattern `s p o [ns]`: subject - predicate - object - name space (optional)."
  
  (get-triples [this s p o] [this s p o ns]))


(deftype GenericTriplesSource [root-source plain-method]
  GenericTriplesSourceProtocol
  (get-triples [this s p o ns]
    (apply plain-method [root-source s p o ns]))
  (get-triples [this s p o]
    (apply plain-method [root-source s p o (r/context-array)])))


(defmulti triples-wrapper-factory "Creates a wrapper for different triples sources.
   Currently it supports `Model` and `RepositoryConnection`." (fn [rdf-source] (type rdf-source)))


;; The anonymous functions put as second argument of the creator are the plain-methods.
(defmethod triples-wrapper-factory Model [source-model]
  (GenericTriplesSource. source-model (fn [src s p o ns] (.filter src s p o ns))))

(defmethod triples-wrapper-factory RepositoryConnection [source-connection]
  (GenericTriplesSource. source-connection #(.getStatements %1 %2 %3 %4 %5)))


