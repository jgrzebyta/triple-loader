(ns rdf4j.located-sail-model-test
  (:require [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.core :as c]
            [rdf4j.repository :as r]
            [rdf4j.repository.sails :as sail]
            [rdf4j.utils :as u])
  (:import java.io.File
           java.nio.file.Paths
           org.apache.commons.io.FileUtils
           rdf4j.models.LocatedSailModel))


(def ^:static rdf-data (u/normalise-path "tests/resources/beet.rdf"))

(t/deftest located-model-simple-test
  (t/testing "MemoryStore with located mirror"
    (let [data-dir (.toFile (u/temp-dir))
          mock-data (c/as-model rdf-data :model-type :memory)
          repository (sail/make-sail-repository :native data-dir :indexes "spoc,pocs")
          model (c/as-model repository)]
      (t/is (instance? File data-dir))
      (log/debugf "data-dir: %s" data-dir)
      (t/is (instance? LocatedSailModel model))
      (t/is (some? (.getDataDir model)))
      (t/is (= (.getDataDir model) data-dir))
      (t/is (<  0 (count mock-data)))
      
      ;; load data to model
      (.addAll model mock-data)
      
      ;; tests repository and model
      (t/is (< 0 (count model)))
      (.commit model)      ;; model MUST be committed/closed before dataset will be visible on repository level
      (t/is (< 0 (count (u/get-all-statements repository))))
      (FileUtils/deleteDirectory data-dir))))
