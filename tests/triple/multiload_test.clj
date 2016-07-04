(ns triple.multiload-test
  (:use [sparql :exclude [-main]]
        [triple.repository]
        [triple.loader-test :import [test-repository]]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.pprint :as pp])
  (:import [java.io StringWriter]))


(def +datasets+ '({:data-file "./tests/resources/22-rdf-syntax-ns.ttl" :type "turtle"}
                  {:data-file "./tests/resources/rdf-schema.rdf" :type "rdfxml"}
                  {:data-file "./tests/beet.rdf" :type "rdfxml"}))


(deftest test-load-multidata "load multiple data."
  (let [repo (make-repository-with-lucene)]
    (load-multidata repo +datasets+)
    (testing "count repository content"
      (let [sts (get-statements repo nil nil nil false (context-array))
            wrt (StringWriter. 100)]
        (pp/pprint sts wrt)
        (log/trace "Fount statements: " (.toString wrt))
        (log/debug "Count statements: " (count sts))
        (is (> (count sts) 0))
        )
      )
    (delete-temp-repository)
    ))
