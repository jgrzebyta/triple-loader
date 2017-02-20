(ns rdf4j.sparql-processor-test
  (:require [rdf4j.sparql.processor :as sp]
            [clojure.test :as t]
            [clojure.tools.logging :as log]))


(t/deftest otest-simple-sparql-processing
  (t/testing "Simple sparql"
    (let [data #{"tests/resources/yeastract_raw.ttl"}
          sparql
          "prefix r: <urn:raw:yeastract#>
select ?term ?term_id where {
?term r:Yeast_id ?term_id
} limit 10"]
      (sp/with-sparql [:query sparql :data #{"tests/resources/yeastract_raw.ttl"} :result out]
        (t/is (= 10 (count out)))
        (t/is (= 10 (count out)))
        )
      )))
