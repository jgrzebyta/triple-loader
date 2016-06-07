(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :version "0.1.3-SNAPSHOT"
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.clojure/tools.logging "0.3.1"]
                          [clj-pid/clj-pid "0.1.2"]
                          [commons-io/commons-io "2.5"]
                          [org.eclipse.rdf4j/rdf4j-repository-http "2.0M1" :exclusions [commons-io org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-runtime "2.0M1" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-repository-sail "2.0M1" :scope "test" :exclusions [org.slf4j/slf4j-api]]
                          [org.slf4j/jcl-over-slf4j "1.7.21"]
                          [org.apache.logging.log4j/log4j-slf4j-impl "2.5"]
                          [org.apache.logging.log4j/log4j-core "2.5"]])

(require '[clojure.test :as test])

(task-options!
 pom {:project (get-env :project) :version (get-env :version)}
 aot { :namespace '#{triple.repository triple.loader sparql} })

(deftask run-test "Run unit tests"
  [t test-name NAME str "Test to execute. Run all tests if not given."]
  *opts*
  (set-env! :source-paths #(conj % "tests"))
  (println (format "%s" (get-env :source-paths)))
  (use '[triple.loader-test]
       '[triple.mock]
       '[triple.context-test]
       '[sparql-test])
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
   (pom)
   (aot)
   (jar)
   (target)))

(deftask build-standalone
  "Build standalone version" []
  (comp
   (pom)
   (aot)
   (uber)
   (jar :file (format "%s-%s-standalone.jar" (name (get-env :project))
                      (get-env :version)))
   (target)))
