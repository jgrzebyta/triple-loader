(ns triple.context-test
  (:gen-class)
  (:use [triple.loader-test :only [count-statements]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as jio]
        [clojure.test]
        [triple.repository]
        [triple.reifiers]
        [triple.utils])
  (:import [org.openrdf.repository RepositoryConnection]
           [org.openrdf.query QueryLanguage]
           [org.openrdf.rio Rio RDFFormat ParserConfig]))


(def ^:dynamic *context-string* "urn:graph/beet")

(defn context-instance-factory [context-string]
  (partial (fn [context-string connection]
             (let [vf (value-factory connection)]
               (.createIRI vf context-string))) context-string))

(def ^:dynamic *ci-factory* (context-instance-factory *context-string*))


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
        (let [context-instance (*ci-factory* con)
              ^Resource rnil nil
              all-triples-total (.getStatements con nil nil nil)
              ;all-triples-no-cont (.getStatements con nil nil nil false rnil)
              #_all-triples #_(.getStatements con nil nil nil (boolean false) context-instance)]
          (log/debug (format "no. triples is %d." (count all-triples-total)))							 ; display number of triples
          #_(log/debug (format "no. triples witout context is %d" (count all-triples-no-cont)))
          #_(log/debug (format "no. triples in context '%s' is %d" *context-string* (count all-triples)))
          #_(is (< 0 (count (iter-seq all-triples-total)))
              (format "no. triples is %d but should be greater than 0" (count all-triples-total)))
          #_(is (= 0 (count (iter-seq all-triples-no-cont)))
              (format "no. triples witout context is %d but should be 0" (count all-triples-no-cont)))
          #_(is (< 0 (count (iter-seq all-triples)))
              (format "no. triples in context '%s' is %d but should be greater than 0" *context-string* (count all-triples)))
          )
        ))))
