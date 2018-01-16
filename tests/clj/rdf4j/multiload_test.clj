(ns rdf4j.multiload-test
  (:use [rdf4j.loader :exclude [-main]]
        [rdf4j.repository]
        [rdf4j.loader-test :import [test-repository]]
        [rdf4j.core :as c]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.pprint :as pp])
  (:import [java.io StringWriter]))


(def +datasets+ '("./tests/resources/22-rdf-syntax-ns.ttl" "./tests/resources/rdf-schema.rdf" "./tests/resources/beet.rdf"))


(deftest test-load-multidata "load multiple data."
  (let [repo (make-repository-with-lucene)
        data-size (load-multidata repo +datasets+)]
    (testing "count repository content"
      (let [sts (c/get-statements repo nil nil nil false (context-array))
            wrt (StringWriter. 100)]
        (pp/pprint sts wrt)
        (log/trace "Fount statements: " (.toString wrt))
        (log/debug "Count statements: " (count sts))
        (is (> (count sts) 0))
        (is (= data-size (count sts)))))
    (.shutDown repo)
    (delete-context)
    (shutdown-agents)))
