

(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.clojure/tools.logging "0.3.1"]
                          [clj-pid/clj-pid "0.1.2"]
                          [commons-io/commons-io "2.5"]
                          [org.eclipse.rdf4j/rdf4j-repository-http "2.1.2" :exclusions [commons-io org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-runtime "2.1.2" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-repository-sail "2.1.2" :scope "test" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-rio-trig "2.1.2"]
                          [ch.qos.logback/logback-classic "1.1.7"]
                          [boot/core "2.6.0" :scope "test"]
                          [degree9/boot-semver "1.3.6" :scope "test"]])

(require '[degree9.boot-semver :refer :all]
         '[clojure.test :as test]
         '[clojure.pprint :as pp]
         '[boot.util :as util]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(defn current [& _] "12")

(task-options!
 version {:minor 'one :patch 'current :include true}
 pom {:project (get-env :project) }
 aot {:namespace '#{rdf4j.repository rdf4j.loader rdf4j.sparql rdf4j.version rdf4j.dump}})


(deftask testing "Attach tests/ directory to classpath." []
  (set-env! :source-paths #(conj % "tests"))
  identity)


(deftask run-test "Run unit tests"
  [t test-name NAME str "Test to execute. Run all tests if not given."]
  *opts*
  (testing)
  (println (format "Repositories: %s" (get-env :repositories)))
  (use '[rdf4j.loader-test]
       '[rdf4j.context-test]
       '[rdf4j.multiload-test]
       '[rdf4j.mock-test]
       '[rdf4j.sparql-test])
  (if (nil? (:test-name *opts*))
    (do
      (println "Run all tests")
      (test/run-all-tests))
    (do
      (println (format "Run test: %s" (:test-name *opts*)))
      (test/test-var (resolve (symbol (:test-name *opts*))))
      )))

(deftask build
  "Build without dependencies" []
  (comp
   (version)
   (jar)
   (pom)
   (aot)
   (jar)
   (target)))

(deftask build-standalone
  "Build standalone version" []
  (comp
   (version)
   (pom)
   (aot)
   (uber)
   (jar :file (format "%s-standalone.jar" (name (get-env :project))))
   (target)))
