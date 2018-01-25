(ns rdf4j.located-sail-model-test
  (:require [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.core :as c]
            [rdf4j.repository.sails :as sail]
            [rdf4j.utils :as u])
  (:import java.io.File
           org.apache.commons.io.FileUtils
           rdf4j.models.LocatedSailModel))

(t/deftest located-model-simple-test
  (t/testing "MemoryStore with located mirror"
    (let [data-dir (.toFile (u/temp-dir))
          repository (sail/make-sail-repository :native data-dir :indexes "spoc,pocs")
          model (c/as-model repository)]
      (t/is (instance? File data-dir))
      (log/debugf "data-dir: %s" data-dir)
      (t/is (instance? LocatedSailModel model))
      (t/is (some? (.getDataDir model)))
      (t/is (= (.getDataDir model) data-dir))
      (FileUtils/deleteDirectory data-dir))))
