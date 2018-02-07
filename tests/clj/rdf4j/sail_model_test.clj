(ns rdf4j.sail-model-test
  (:require [clojure.java.io :as io]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.collections :as c]
            [rdf4j.core :as co]
            [rdf4j.repository :as r]
            [rdf4j.repository.sails :as s]
            [rdf4j.utils :as u])
  (:import org.eclipse.rdf4j.model.Model
           org.eclipse.rdf4j.model.util.Models))

(def ^{:static true} vf (u/value-factory))


(t/deftest sail-model-test
  (t/testing "Test Model loading"
    (let [file-obj (io/file "tests/resources/collections/type-list.ttl")
          repository (doto (s/make-sail-repository :memory nil)
                       (co/load-data file-obj))
          model (co/as-model repository)]
      (t/is (instance? Model model))
      (t/is (not (.isEmpty model)))
      (log/tracef "model instance: %s" (.toString model))
      (log/debugf "model size: %d" (.size model))
      (.close model))))


(t/deftest sail-model-collection-test
  (let [data-file (io/file "tests/resources/collections/type-list.ttl")
        repository (doto (s/make-sail-repository :memory nil)
                     (co/load-data data-file))]
    (t/testing "Simple test"
      (let [model (co/as-model repository)
            root (-> (.filter model nil (.createIRI vf "http://www.eexample.org/data#" "data") nil (r/context-array))
                                  (Models/object)
                                  (.get))
            seq-data (c/rdf->seq model root [])]
        (t/is (< 0 (count model)))
        (t/is (< 0 (count seq-data)))
        (log/debugf "List: %s" (prn-str seq-data))
        (.close model)))))
