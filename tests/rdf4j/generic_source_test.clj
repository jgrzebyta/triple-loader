(ns rdf4j.generic-source-test
  (:require [rdf4j.loader :as l]
            [rdf4j.repository :as r]
            [rdf4j.models :as m]
            [rdf4j.triples-source.wrappers :as w]
            [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [org.eclipse.rdf4j.model Model]
           [rdf4j.triples_source.wrappers GenericTriplesSourceProtocol GenericTriplesSource]))


(t/deftest generic-source-test
  (t/testing "Test list"
    (let [model (->
                 (io/file "tests/resources/collections/type-list.ttl")
                 (m/loaded-model))
          source (w/triples-wrapper-factory model)
          triples (.get-all-triples source)]
      (t/is (not (empty? triples)))
      (t/is (= (count triples) 33))
      (log/infof "Number of triples: %d" (count triples))
      (log/infof "All types: %s" (instance? GenericTriplesSource source)))))



