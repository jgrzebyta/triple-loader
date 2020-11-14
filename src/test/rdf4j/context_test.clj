(ns rdf4j.context-test
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as jio]
            [clojure.test :as t]
            [rdf4j.loader :as l]
            [rdf4j.repository :as repo]
            [rdf4j.reifiers :as ref]
            [rdf4j.utils :as u]
            [rdf4j.core :as c])
  (:import [org.eclipse.rdf4j.model Resource IRI URI Value]
           [org.eclipse.rdf4j.repository RepositoryConnection]
           [org.eclipse.rdf4j.query QueryLanguage]
           [org.eclipse.rdf4j.rio Rio RDFFormat ParserConfig]))


(def context-string "urn:graph/beet")


(t/deftest context-loading
  (t/testing "load data into named graph using default API"
    (let [rdf-h ref/counter-commiter
          file-obj (jio/file (jio/resource "./resources/beet.rdf"))
          repos (repo/make-mem-repository)]
      (c/load-data repos file-obj :rdf-handler rdf-h :context-uri context-string)
      (repo/with-open-repository [con repos]
        (let [all-triples-total (c/get-statements con nil nil nil false (u/context-array))                
              all-triples-no-cont (c/get-statements con nil nil nil false (u/context-array nil))          
              all-triples (c/get-statements con nil nil nil false (u/context-array con context-string))]
          (log/debug (format "No. triples is %d." (count all-triples-total)))                           
          (log/debug (format "No. triples witout context is %d" (count all-triples-no-cont)))          
          (log/debug (format "No. triples in context '%s' is %d" context-string (count all-triples)))
          (t/is (< 0 (count all-triples-total))
                (format "no. triples is %d but should be greater than 0" (count all-triples-total)))
          (t/is (= 0 (count all-triples-no-cont))
                (format "no. triples witout context is %d but should be 0" (count all-triples-no-cont)))
          (t/is (< 0 (count all-triples))
              (format "no. triples in context '%s' is %d but should be greater than 0" context-string (count all-triples)))
          ))
      (repo/delete-context)))
  (t/testing "load data into named graph using multiloader API"
    (let [rdf-h ref/counter-commiter
          file-obj (jio/file (jio/resource "./resources/beet.rdf"))
          repos (repo/make-mem-repository)
          loaded (l/load-multidata repos [(-> "./resources/beet.rdf"
                                              (jio/resource)
                                              (.getPath))] :rdf-handler rdf-h :context-uri context-string)]
      (log/debugf "loaded %d statements" loaded)
      (repo/with-open-repository [con repos]
        (let [all-triples-total (c/get-statements con nil nil nil false (u/context-array))
              all-triples-no-cont (c/get-statements con nil nil nil false (u/context-array nil))
              all-triples (c/get-statements con nil nil nil false (u/context-array con context-string))]
          (log/debug (format "No. triples is %d." (count all-triples-total)))                           
          (log/debug (format "No. triples witout context is %d" (count all-triples-no-cont)))          
          (log/debug (format "No. triples in context '%s' is %d" context-string (count all-triples)))
          (t/is (< 0 (count all-triples-total))
                (format "no. triples is %d but should be greater than 0" (count all-triples-total)))
          (t/is (= 0 (count all-triples-no-cont))
                (format "no. triples witout context is %d but should be 0" (count all-triples-no-cont)))
          (t/is (< 0 (count all-triples))
              (format "no. triples in context '%s' is %d but should be greater than 0" context-string (count all-triples)))
          ))
      (repo/delete-context))))

(t/deftest simple-context-loading
  (t/testing "Load data into named graph using low level API"
    (let [counter (atom 0)
          pars (Rio/createParser RDFFormat/RDFXML)
          file-obj (jio/file (jio/resource "./resources/beet.rdf"))]
      (repo/with-open-repository [^RepositoryConnection con (repo/make-mem-repository)]
        (.setRDFHandler pars (ref/counter-commiter con (u/context-array nil context-string) counter))
        (with-open [fr (jio/reader file-obj)]
          (.parse pars fr (.toString (.toURI file-obj)))
          (.commit con))
        (log/debug "All data should be loaded... validation")
        (let [all-triples-total (c/get-statements con nil nil nil false (u/context-array))
              all-triples-no-cont (c/get-statements con nil nil nil false (u/context-array nil))
              all-triples (c/get-statements con nil nil nil false (u/context-array con context-string))]
          (log/debug (format "No. triples is %d." (count all-triples-total))) 						; display number of triples
          (log/debug (format "No. triples witout context is %d" (count all-triples-no-cont)))
          (log/debug (format "No. triples in context '%s' is %d" context-string (count all-triples)))
          (t/is (< 0 (count all-triples-total))
              (format "no. triples is %d but should be greater than 0" (count all-triples-total)))
          (t/is (= 0 (count all-triples-no-cont))
              (format "no. triples witout context is %d but should be 0" (count all-triples-no-cont)))
          (t/is (< 0 (count all-triples))
              (format "no. triples in context '%s' is %d but should be greater than 0" context-string (count all-triples))))
        )
      (repo/delete-context))))


