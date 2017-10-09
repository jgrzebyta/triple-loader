(ns rdf4j.triples-source.wrappers
  (:require [rdf4j.repository :as r]
            [rdf4j.loader :as l]
            [rdf4j.collections.utils :as u])
  (:import [org.eclipse.rdf4j.repository RepositoryConnection Repository]
           [org.eclipse.rdf4j.model Model Resource IRI Value]))


(defprotocol GenericTriplesSourceProtocol

  "This protocol describes generic methods for triples retrieving. The implementations of the protocol
   wraps either `Model` or `Repository`. The user is `root-source` agnostic - does not have
   to know the API. 

   `get-triples` methods are defined with arguments pattern `s p o [ns]`: subject - predicate - object - name space (optional).
   Returns instance of `Model`." 
  
  (get-triples [this s p o] [this s p o ns])
  
  (get-all-triples [this]))


(defprotocol ProxySourceFactory

  "Protocol defines factory interface for presenting source as `Repositiry` or as `Model`.  
  "
  (as-repository [this])
  (as-model [this]))


(deftype GenericTriplesSource [root-source plain-method]
  GenericTriplesSourceProtocol
  (get-triples [this s p o ns]
    (apply plain-method [root-source s p o ns]))
  (get-triples [this s p o]
    (u/loaded-model (apply plain-method [root-source s p o (r/context-array)])))
  (get-all-triples [this]
    (r/get-all-statements root-source))

  ProxySourceFactory
  (as-repository [this]
    (case (type root-source)
      Repository root-source
      Model (let [repository (r/make-repository)]
              (l/load-data repository root-source)
              repository)))
  (as-model [this]
    (case (type root-source)
      Repository (u/loaded-model (r/get-all-statements root-source))
      Model root-source)))


(defmulti triples-wrapper-factory "Creates a wrapper for different triples sources.
   Currently it supports `Model` and `Repository`." (fn [rdf-source] (type rdf-source)))


;; The anonymous functions put as second argument of the creator are the plain-methods.
(defmethod triples-wrapper-factory Model [source-model]
  (GenericTriplesSource. source-model #(.filter %1 %2 %3 %4 %5)))

(defmethod triples-wrapper-factory RepositoryConnection [source-connection]
  (GenericTriplesSource. source-connection #(r/with-open-repository [cn %1]
                                              (.getStatements cn %2 %3 %4 %5))))


(defmethod l/load-data GenericTriplesSource [repository data-source]
  (l/load-data repository (.as-model data-source)))
