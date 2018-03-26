(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :description "Triple-loader provides command line and clojure script writing tools for managing RDF data."
          :url "http://www.adalab-project.org/"
          :dependencies '[[org.clojure/clojure "1.9.0"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.clojure/tools.logging "0.4.0"]
                          [clj-pid/clj-pid "0.1.2"]
                          [commons-io/commons-io "2.6"]
                          [org.eclipse.rdf4j/rdf4j-repository-http "2.3.0" :exclusions [commons-io org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-sail-memory "2.3.0" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-sail-nativerdf "2.3.0" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-queryresultio-text "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-repository-sail "2.3.0" :scope "test" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-repository-contextaware "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-lucene-spin "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-trig "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-trix "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-rdfxml "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-rdfjson "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-binary "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-ntriples "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-nquads "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-n3 "2.3.0"]
                          [org.eclipse.rdf4j/rdf4j-rio-jsonld "2.3.0"]
                          [ch.qos.logback/logback-classic "1.2.3"]
                          [org.slf4j/jcl-over-slf4j "1.7.9"]
                          [degree9/boot-semver "1.7.0" :scope "test"]
                          [adzerk/boot-test "1.2.0" :scope "test"]
                          [boot-codox "0.10.3" :scope "test"]]
          :repositories { "sonatype-public" { :url "https://oss.sonatype.org/content/groups/public/" }
                         "central" { :url "http://repo1.maven.org/maven2/"}
                         "clojars" { :url "https://clojars.org/repo/" }
                         })

(require '[boot.core :as boot]
         '[degree9.boot-semver :refer :all]
         '[adzerk.boot-test :refer :all :as at]
         '[boot.util :as util]
         '[codox.boot :refer [codox]])

;; this line prevents confusing the deployer with dependencies` pom.xml files
(alter-var-root #'boot.pod/standard-jar-exclusions (constantly (conj boot.pod/standard-jar-exclusions #"/pom\.xml$")))

  (def +version+ "0.2.3")

(task-options!
 version {:minor 'two :patch 'three :include false :generate 'rdf4j.version}
 pom {:project (get-env :project)
      :scm {:url "https://github.com/jgrzebyta/triple-loader"
            :connection "scm:git:ssh://github.com/jgrzebyta/triple-loader.git"
            :developerConnection "scm:git:ssh://git@github.com/jgrzebyta/triple-loader.git"}}
 aot {:namespace #{'rdf4j.models.located-sail-model} }
 codox {:name "triple-loader" :version +version+ :output-path "gh-pages" :description "Some description." :metadata {:doc/format :markdown}})

(deftask develop
  "Build SNAPSHOT version of jar"
  []
  (version :patch 'inc :develop true :pre-release 'snapshot :generate 'rdf4j.version))

(deftask prepare
  "Prepare coding: (comp (devel) (aot))"
  []
  (comp
   (develop)
   (aot)))

(deftask testing "Attach tests/ directory to classpath." []
  (set-env! :source-paths #(into % ["tests/resources" "tests/clj"])))

(deftask run-test "Run unit tests"
  [t test-name NAME str "Test to execute. Run all tests if not given."]
  *opts*
  (testing)
  (if-let [test-name (:test-name *opts*)]
    (let [pattern (re-pattern test-name)
          to-eval `(at/test :filters #{ '(re-find ~pattern (str ~'%)) } )]
      (println (format "Run test: %s" test-name))
      (eval to-eval))
    (do
      (println "Run all tests")
      (at/test))))

(deftask build
  "Build jar without dependencies and not compiled" []
  (comp
   (pom)
   (aot)
   (sift :add-resource ["src/"]) ;; add source files as well
   (build-jar)
   (target)))

(deftask build-standalone
  "Build standalone version"
  []
  (comp
   (pom)
   (aot :all true)
   (uber)
   (jar :file (format "%s-standalone.jar" (str (name (get-env :project)) "-" +version+)))
   (target)))
