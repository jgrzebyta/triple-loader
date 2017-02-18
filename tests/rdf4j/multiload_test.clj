(ns rdf4j.multiload-test
  (:use [rdf4j.loader :exclude [-main]]
        [rdf4j.repository]
        [rdf4j.loader-test :import [test-repository]]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.pprint :as pp])
  (:import [java.io StringWriter]))


(def +datasets+ '("./tests/resources/22-rdf-syntax-ns.ttl" "./tests/resources/rdf-schema.rdf" "./tests/beet.rdf"))


(deftest test-load-multidata "load multiple data."
  (let [repo (make-repository-with-lucene)]
    (load-multidata repo +datasets+)
    (testing "count repository content"
      (let [sts (get-statements repo nil nil nil false (context-array))
            wrt (StringWriter. 100)]
        (pp/pprint sts wrt)
        (log/trace "Fount statements: " (.toString wrt))
        (log/debug "Count statements: " (count sts))
        (is (> (count sts) 0))))
    (.shutDown repo)
    (delete-context)
    ))
