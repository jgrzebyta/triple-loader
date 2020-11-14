(ns rdf4j.mock-test
  (:use [clojure.java.io :as jio]
        [clojure.test :as test]
        [clojure.tools.logging :as log])
  (:require [clojure.java.io :as io])
  (:import [org.eclipse.rdf4j.rio Rio RDFFormat RDFParserFactory]
           [org.eclipse.rdf4j.rio.turtle TurtleParserFactory]
           [org.eclipse.rdf4j.rio.helpers StatementCollector]))


(defn read-rdf-file "Reads RDF file"
  [filename]
  (let [file-obj (jio/file filename)
        parser-format (.get (Rio/getParserFormatForFileName (.getName file-obj)))
        rdf-parser (Rio/createParser parser-format)
        collector (StatementCollector.)]
    (.setRDFHandler rdf-parser collector)
    (log/info "Parser: " (type rdf-parser))
    ;; run parsing process
    (with-open [rdr (jio/input-stream filename)]
      (.parse rdf-parser rdr "urn:data"))
    collector))

(deftest test-reading "Provides main reading job" []
  (let [collector (read-rdf-file (-> "./resources/22-rdf-syntax-ns.ttl"
                                     (io/resource)
                                     (io/file)))]
    (testing "Test RDF file reading"
;;      (is (instance? collector))
      (is (< 0 (count (.getNamespaces collector))))
      (println (format "namespaces: %s" (.getNamespaces collector))))))
