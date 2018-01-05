(ns rdf4j.located-sail-model-test
  (:require [clojure.test :as t]
            [clojure.tools.logging :as log]
            [rdf4j.models.located-sail-model]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u])
  (:import [java.io File]
           [org.apache.commons.io FileUtils]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [rdf4j.models LocatedSailModel]))

(t/deftest simple-test
  (t/testing "MemoryStore with located mirror"
    (let [store (MemoryStore.)
          repository (-> (doto store
                           (r/make-sail-datadir u/temp-dir "fortest"))
                         r/make-repository)
          data-dir (.getDataDir repository)
          model (LocatedSailModel. repository false)]
      (t/is (instance? File data-dir))
      (log/debugf "data-dir: %s" data-dir)
      (t/is (instance? LocatedSailModel model))
      (t/is (some? (.getDataDir model)))
      (t/is (= (.getDataDir model) data-dir))
      (FileUtils/deleteDirectory data-dir))))
