(ns rdf4j.sparql-processor-test
  (:require [rdf4j.sparql.processor :as sp]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [clojure.test :as t]
            [clojure.tools.logging :as log])
  (:import [org.eclipse.rdf4j.model.impl SimpleValueFactory]))


(t/deftest test-simple-sparql-processing
  (t/testing "Simple sparql"
    (let [data (list "tests/resources/yeastract_raw.ttl")
          sparql "prefix r: <urn:raw:yeastract#>
select ?term ?term_id where {
?term r:Yeast_id ?term_id
} limit 10"]
      (sp/with-sparql [:query sparql
                       :data data
                       :result out]
        (t/is (= 10 (count out)))
        (t/is (= 10 (count out)))
        (loop [its out]
          (when (some? (first its))
            (log/debug (format "term ID: '%s'       line: '%s'"
                                       (.getValue (first its) "term_id")
                                       (.getValue (first its) "term")
                               ))
            (recur (rest its))
            ))
        )
      (r/delete-context)))
  (t/testing "Remote resource using federated query"
    (let [sparql "PREFIX db:	<http://dbpedia.org/ontology/>
SELECT ?label ?abstract
WHERE
{
  SERVICE <http://DBpedia.org/sparql>
  { SELECT *
    WHERE { <http://dbpedia.org/resource/Robert_Burns> rdfs:label ?label;
    db:abstract ?abstract .
    FILTER (lang(?label) = \"en\") .
    FILTER (lang(?abstract) = \"en\")
    }
  }
}"]
      (sp/with-sparql [:query sparql :result out]
        (t/is (< 0 (count out)))
        (loop [its out]
          (when (some? (first its))
            (log/debug (format "\nlabel: '%s'\n\nabstract: '%s'\n\n" (.getValue (first its) "label") (.getValue (first its) "abstract")))
            (recur (rest its)))))
      (r/delete-context)))

  (t/testing "query with binding"
    (let [vf (SimpleValueFactory/getInstance)
          binding {:lbl (.createLiteral vf "Robert Burns" "en")}
          sparql "PREFIX db:	<http://dbpedia.org/ontology/>
SELECT ?item ?abstract
WHERE
{
  SERVICE <http://DBpedia.org/sparql>
  { SELECT *
    WHERE { ?item rdfs:label ?lbl;
    db:abstract ?abstract .
    FILTER (lang(?abstract) = \"en\")
    }
  }
}"]
      (sp/with-sparql [:query sparql :result out :binding binding]
        (t/is (< 0 (count out)))
        (loop [its out]
          (when (some? (first its))
            (log/debug (format "\nitem: '%s'\n\nabstract: '%s'\n\n" (.getValue (first its) "item") (.getValue (first its) "abstract")))
            (recur (rest its)))))
      (r/delete-context))))


(defn export-country [query-result]
  (mapcat #(.getLabel (.getValue %1 "country")) query-result))

(t/deftest test-sparql-processing
  (t/testing "separate repository"
    (let [repo (r/make-repository)
          sparql "prefix d: <file:/tmp2/beet-1.>
select distinct ?country 
where
{[] d:csvCountries ?country}"
          data '("tests/resources/beet.rdf")]
      (sp/with-sparql [:repository repo :query sparql :result out :data data]
        (t/is (< 0 (count out)))
                                 ))
      (r/delete-context)))
