(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :description "Triple-loader provides command line and clojure script writing tools for managing RDF data."
          :url "http://www.adalab-project.org/"
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.clojure/tools.logging "0.4.0"]
                          [clj-pid/clj-pid "0.1.2"]
                          [commons-io/commons-io "2.5"]
                          [org.eclipse.rdf4j/rdf4j-repository-http "2.3-SNAPSHOT" :exclusions [commons-io org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-runtime "2.3-SNAPSHOT" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-repository-sail "2.3-SNAPSHOT" :scope "test" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-lucene-spin "2.3-SNAPSHOT"]
                          [org.eclipse.rdf4j/rdf4j-rio-trig "2.3-SNAPSHOT"]
                          [ch.qos.logback/logback-classic "1.2.3"]
                          [degree9/boot-semver "1.4.4" :scope "test"]])

(require '[degree9.boot-semver :refer :all]
         '[clojure.test :as test]
         '[clojure.pprint :as pp]
         '[boot.util :as util]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

;; this line prevents confusing the deployer with dependencies` pom.xml files
(alter-var-root #'boot.pod/standard-jar-exclusions (constantly (conj boot.pod/standard-jar-exclusions #"/pom\.xml$")))


(task-options!
 version {:minor 'two :patch 'one :include false}
 pom {:project (get-env :project) }
 aot {:all true})

(deftask develop
  "Build SNAPSHOT version of jar"
  []
  (version :patch 'inc :develop true :pre-release 'snapshot))


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
       '[rdf4j.sparql-test]
       '[rdf4j.sparql-processor-test])
  (if (nil? (:test-name *opts*))
    (do
      (println "Run all tests")
      (test/run-all-tests))
    (do
      (println (format "Run test: %s" (:test-name *opts*)))
      (test/test-var (resolve (symbol (:test-name *opts*))))
      )))

(deftask build
  "Build jar without dependencies and not compiled" []
  (comp
   (pom)
   (aot) ;; perform compilation
   (sift :add-resource ["src/"]) ;; add suurce files as well
   (jar)
   (target)
   (install)))

(deftask build-standalone
  "Build standalone version"
  []
  (comp
   (pom)
   (aot)
   (uber)
   (jar :file (format "%s-standalone.jar" (str (name (get-env :project))) ))
   (target)))
