
(def sesame-version "4.0.1")
(def maven-central ["central1" "http://repo1.maven.org/maven2/"])


(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :version "0.0.1-SNAPSHOT"
          :dependencies '[[org.clojure/clojure "1.7.0"]
                          [org.clojure/tools.cli "0.3.3"]
                          [org.clojure/tools.logging "0.3.1"]
                          [org.openrdf.sesame/sesame-repository-http "4.0.1" :exclusions [commons-io]]
                          [org.openrdf.sesame/sesame-runtime "4.0.1"]
                          [org.openrdf.sesame/sesame-repository-sail "4.0.1" :scope "test"]
                          [org.slf4j/jcl-over-slf4j "1.7.10"]
                          [org.apache.logging.log4j/log4j-slf4j-impl "2.5"]
                          [org.apache.logging.log4j/log4j-core "2.5"]]
          :repositories #(conj % maven-central))

(require '[clojure.test :as test])

(task-options!
 pom {:project (get-env :project) :version (get-env :version)}
 aot { :namespace '#{triple.repository triple.loader} }
 jar { :main 'triple.loader })

(deftask run-testing "Run unit tests"
  [t test-name NAME str "Test to execute. Run all tests if not given."]
  *opts*
  (set-env! :source-paths #(conj % "tests"))
  (println (format "%s" (get-env :source-paths)))
  (use '[triple.loader-test]
       '[triple.mock])
  (if (nil? (:test-name *opts*))
    (do
      (println "Run all tests")
      (test/run-all-tests #"triple.*"))
    (do
      (println (format "Run test: %s" (:test-name *opts*)))
      (test/test-var (resolve (symbol (:test-name *opts*))))
      )
    ))


(deftask build
  "Build without dependencies" []
  (comp
   (pom)
   (aot)
   (jar)))

(deftask build-standalone
  "Build standalone version" []
  (comp
   (pom)
   (aot)
   (uber)
   (jar :file (format "%s-%s-standalone.jar" (name (get-env :project))
                                             (get-env :version))))
  )
