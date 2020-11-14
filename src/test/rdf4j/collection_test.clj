(ns rdf4j.collection-test
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.collections :as c]
            [rdf4j.collections.utils :as cu]
            [rdf4j.core :as co]
            [rdf4j.models :as m]
            [rdf4j.repository :as r]
            [rdf4j.repository.sails :as sail]
            [rdf4j.utils :as u])
  (:import org.apache.commons.io.FileUtils
           [org.eclipse.rdf4j.model BNode Model]
           org.eclipse.rdf4j.model.impl.LinkedHashModel
           org.eclipse.rdf4j.model.util.Models
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS XMLSchema]
           rdf4j.models.LocatedSailModel
           rdf4j.protocols.ModelHolder))

(def ^{:static true} vf (u/value-factory))

(t/deftest loaded-model
  (let [file-obj (io/file (io/resource "resources/collections/multisubj.ttl"))]
    (t/testing "load collections into standard model."
      (let [repository (r/make-repository)]
        (co/load-data repository file-obj)
        (let [^Model model (co/as-model (u/get-all-statements repository))]
          (t/is (not (.isEmpty model)))
          (log/debugf "Number of statements: %d" (.size model))
          (t/is (not (m/single-subjectp model))))))
    (t/testing "load collections into memory sail ."
      (let [repository (r/make-repository)]
        (co/load-data repository file-obj)
        (let [^Model model (co/as-model (u/get-all-statements repository) :model-type :solid)
              ^Model model2 (co/as-model (u/get-all-statements repository))
              data-dir (.getDataDir model)]
          (t/is (not (.isEmpty model)))
          (log/debugf "Number of statements: %d" (.size model))
          (t/is (instance? LocatedSailModel model))
          (t/is (instance? LinkedHashModel model2))
          (t/is (not (m/single-subjectp model)))
          (t/is (some? data-dir))
          (log/infof "data-dir: %s" (str data-dir))
          (FileUtils/deleteDirectory data-dir))))))


(t/deftest single-subject-test
  (t/testing "Test single-subjectp predicate"
    (let [file-obj (io/file (io/resource "resources/collections/multisubj2.ttl" ))
          repository (r/make-repository)]
      (co/load-data repository file-obj)
      (let [^Model model (co/as-model (u/get-all-statements repository))]
        (t/is (not (m/single-subjectp model)))))
    
    (let [file-obj (io/file (io/resource "resources/collections/multisubj3.ttl" ))
          repository (r/make-repository)]
      (co/load-data repository file-obj)
      (let [^Model model (co/as-model (u/get-all-statements repository))]
        (t/is (m/single-subjectp model))))))

(t/deftest loaded-model-test
  (t/testing "Test Model loading"
    (let [file-obj (io/file (io/resource "resources/collections/type-list.ttl" ))
          model (co/as-model file-obj)]
      (t/is (instance? Model model))
      (t/is (not (.isEmpty model)))
      (log/debugf "model instance: %s" (.toString model))
      (log/debugf "model size: %d" (.size model)))))


(t/deftest collection-type-test
    (t/testing "Collection type test for rdf:List"
      (let [model (->
                   (io/resource "resources/collections/type-list.ttl")
                   (io/file)
                   (co/as-model))]
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
                   (io/resource "resources/collections/type-container.ttl")
                   (io/file)
                   (co/as-model))
            collection-root (-> (.filter model nil (.createIRI vf "http://www.eexample.org/data#" "data") nil (r/context-array))
                                (Models/object)
                                (.get))]
        (t/is (some? collection-root))
        (let [submodel (.filter model collection-root nil nil (r/context-array))
              collection-type (cu/collection-type submodel)]
          (log/debugf "Collection type: %s" collection-type)
          (t/is (= collection-type RDFS/CONTAINER))))))

(t/deftest rdf-coll-test
  (let [data-source (->
                     (io/resource "resources/collections/type-list.ttl")
                     (io/file)
                     (co/as-model))
        data-dir (.getDataDir data-source)]
    (t/testing "simple rdf->seq"
      (let [collection-root (-> (m/rdf-filter data-source nil (.createIRI vf "http://www.eexample.org/data#" "data") nil)
                                (Models/object)
                                (.get))
            to-test (c/rdf->seq data-source collection-root [])]
        (t/is (not (empty? to-test)))
        (t/is (= 3 (count to-test)))
        (t/is (vector? to-test))
        (t/are [p x] (not (p x))
          set? to-test
          list? to-test)
        (log/debug (with-out-str (pp/pprint to-test)))))
    (t/testing "hetero rdf->seq"
      (let [collection-root (-> (m/rdf-filter data-source nil (.createIRI vf "http://www.eexample.org/data#" "data1") nil)
                                (Models/object)
                                (.get))
            to-test (c/rdf->seq data-source collection-root #{})]
        (t/is (not (empty? to-test)))
        (t/is (= 2 (count to-test)))
        (t/is (set? to-test))
        (t/are [p x] (not (p x))
          vector? to-test
          list? to-test)
        (log/debug (with-out-str (pp/pprint to-test)))))
    (t/testing "nested rdf"
      (let [collection-root (-> (m/rdf-filter data-source nil (.createIRI vf "http://www.eexample.org/data#" "data2") nil)
                                Models/object
                                .get)
            to-test (c/rdf->seq data-source collection-root [])]
        (let [pair1 (first to-test)
              triples (m/rdf-filter data-source pair1 nil nil)]
          #_(log/debugf "first pair: %s"  (c/rdf->seq data-source (first to-test) []))
          #_(log/debugf "\n%s\n" (m/print-model triples RDFFormat/NTRIPLES)))
        (let [i (first to-test)
              pair (c/rdf->seq data-source i [])]
          (t/is (= (-> pair
                       first
                       .getLabel) "var1"))
          (t/is (= (double 1.56) (-> pair
                                     second
                                     .doubleValue))))
        (t/is (not (empty? to-test)))))
    (.close data-source)
    (FileUtils/deleteDirectory data-dir)))

(t/deftest rdf-coll-repository
  (let [data-file (io/file (io/resource "resources/collections/type-list.ttl"))
        repository (doto (sail/make-sail-repository :memory nil)
                     (co/load-data data-file))]
    #_(t/testing "Collectons from repository"
      (let [root (co/rdf-filter-object repository
                                       (.createIRI vf "http://www.eexample.org/data#" "./resources_1")
                                       (.createIRI vf "http://www.eexample.org/data#" "data"))
            coll (c/rdf->seq repository root [])]
        (t/is (instance? BNode root))
        (t/is (= 3 (count coll)))
        (log/debugf "Collection: %s" coll)))
    (t/testing "Collectons from repository"
      (let [root (co/rdf-filter-object repository
                                       (.createIRI vf "http://www.eexample.org/data#" "./resources_no")
                                       (.createIRI vf "http://www.eexample.org/data#" "no_data"))]
        (log/debugf "Root: %s" root)))
    (.shutDown repository)))

(t/deftest rdf-protocols
  (let [md (->
            (io/resource "resources/beet.trig")
            (io/file)
            co/as-model)]
    (t/testing "test protocols"
      (let [root (-> (m/rdf-filter md
                                   nil
                                   (.createIRI vf "file:/tmp2/" "beet-1.csvCountries")
                                   (.createLiteral vf "UnitedKingdom" XMLSchema/STRING))
                     Models/subjectIRI
                     .get)
            submodel (m/rdf-filter md root nil nil)
            gs (ModelHolder. root submodel)]
        (t/is (some? gs))
        (log/debugf "Instance: %5s\n" (binding [*print-dup* false] (println-str gs)))))
    (.close md)
    (FileUtils/deleteDirectory (.getDataDir md))))
