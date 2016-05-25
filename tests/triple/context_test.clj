(ns triple.context-test
  (:gen-class)
  (:use [triple.loader-test :only [count-statements]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.test]
        [triple.repository]
        [triple.reifiers])
  (:import [org.eclipse.rdf4j.model Resource IRI URI Value]
           [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.query QueryLanguage]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig]))


(def ^:dynamic *context-string* "urn:graph/beet")


(deftest context-loading
  (testing "Load data into named graph"
    (let [pars (Rio/createParser RDFFormat/RDFXML)
          file-obj (jio/file "tests/beet.rdf")]
      (with-open-repository [^RepositoryConnection con :memory]
        (.setRDFHandler pars (chunk-commiter con *context-string*))
        (with-open [fr (jio/reader file-obj)]
          (.parse pars fr (.toString (.toURI file-obj)))
          (.commit con))
        (log/debug "All data should be loaded... validation")
        (let [all-triples-total (iter-seq (get-statements con nil nil nil false (context-array)))
              all-triples-no-cont (iter-seq (get-statements con nil nil nil false (context-array nil)))
              all-triples (iter-seq (get-statements con nil nil nil false (context-array con *context-string*)))]
          (log/debug (format "no. triples is %d." (count all-triples-total))) 						; display number of triples
          (log/debug (format "no. triples witout context is %d" (count all-triples-no-cont)))
          (log/debug (format "no. triples in context '%s' is %d" *context-string* (count all-triples)))
          (is (< 0 (count all-triples-total))
              (format "no. triples is %d but should be greater than 0" (count all-triples-total)))
          (is (= 0 (count all-triples-no-cont))
              (format "no. triples witout context is %d but should be 0" (count all-triples-no-cont)))
          (is (< 0 (count all-triples))
              (format "no. triples in context '%s' is %d but should be greater than 0" *context-string* (count all-triples)))
          )
        ))))


