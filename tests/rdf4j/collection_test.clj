(ns rdf4j.collection-test
  (:require [rdf4j.loader :as l]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [rdf4j.collections.utils :as cu]
            [rdf4j.triples-source.wrappers :as w]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [clojure.tools.logging :as log])
  (:import [org.eclipse.rdf4j.model Model]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.model.util Models]))


(t/deftest loaded-model
  (t/testing "load data into model."
    (let [file-obj (io/file "tests/resources/collections/multisubj.ttl")
          repository (r/make-repository)]
      (l/load-data repository file-obj)
      (let [^Model model (cu/loaded-model (r/get-all-statements repository))]
        (t/is (not (.isEmpty model)))
        (log/debugf "Number of statements: %d" (.size model))
        (t/is (not (cu/single-subjectp model)))))))


(t/deftest single-subject-test
  (t/testing "Test single-subjectp predicate"
    (let [file-obj (io/file "tests/resources/collections/multisubj2.ttl" )
          repository (r/make-repository)]
      (l/load-data repository file-obj)
      (let [^Model model (cu/loaded-model (r/get-all-statements repository))]
        (t/is (not (cu/single-subjectp model)))))
    
    (let [file-obj (io/file "tests/resources/collections/multisubj3.ttl" )
          repository (r/make-repository)]
      (l/load-data repository file-obj)
      (let [^Model model (cu/loaded-model (r/get-all-statements repository))]
        (t/is (cu/single-subjectp model))))))

(t/deftest loaded-model-test
  (t/testing "Test Model loading"
    (let [file-obj (io/file "tests/resources/collections/type-list.ttl" )
          model (cu/loaded-model file-obj)]
      (t/is (instance? Model model))
      (t/is (not (.isEmpty model)))
      (log/debugf "model instance: %s" (.toString model))
      (log/debugf "model size: %d" (.size model)))))


(t/deftest collection-type-test
  (let [vf (u/value-factory)]
    (t/testing "Collection type test for rdf:List"
      (let [model (->
                   (io/file "tests/resources/collections/type-list.ttl")
                   (cu/loaded-model))]
        (t/is (not (.isEmpty model)))
        (let [collection-root (-> (.filter model nil (.createIRI vf "http://www.eexample.org/data#" "data") nil (r/context-array))
                                  (Models/object)
                                  (.get))]
          (t/is (some? collection-root))
          (log/debugf "root IRI: %s" (.stringValue collection-root))
          (let [submodel (.filter model collection-root nil nil (r/context-array))
                collection-type (cu/collection-type submodel)]
            (log/debugf "Collection type: %s" collection-type)
            (t/is (= collection-type RDF/LIST))))))

    (t/testing "Collection type test for rdfs:Container"
      (let [model (->
                   (io/file "tests/resources/collections/type-container.ttl")
                   (cu/loaded-model))
            collection-root (-> (.filter model nil (.createIRI vf "http://www.eexample.org/data#" "data") nil (r/context-array))
                                (Models/object)
                                (.get))]
        (t/is (some? collection-root))
        (let [submodel (.filter model collection-root nil nil (r/context-array))
              collection-type (cu/collection-type submodel)]
          (log/debugf "Collection type: %s" collection-type)
          (t/is (= collection-type RDFS/CONTAINER)))))))

