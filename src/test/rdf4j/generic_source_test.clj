(ns rdf4j.generic-source-test
  (:require [rdf4j.utils :as u]
            [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [rdf4j.core :as c])
  (:import [org.eclipse.rdf4j.model Model]))


(t/deftest as-model-test
  (t/testing "Test list"
    (let [model (->
                 (io/resource "resources/collections/type-list.ttl")
                 (io/file)
                 (c/as-model :model-type :memory))
          triples (u/get-all-statements model)]
      (t/is (not (empty? triples)))
      (t/is (= 33 (count triples)))
      (log/debugf "instance of source: %s" (binding [*print-dup* true] (clojure.pprint/pprint (print-dup model *out*))))
      (log/infof "Number of triples in in-model: %d" (count model))
      (log/infof "Number of triples in generic source: %d" (count triples))
      (log/infof "Is empty: %s" (empty? triples))
      (log/infof "All types: %s" (instance? Model model)))))



