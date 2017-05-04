(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :description "Triple-loader provides command line and clojure script writing tools for managing RDF data."
          :url "http://www.adalab-project.org/"
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.clojure/tools.logging "0.3.1"]
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
         '[boot.core :as core]
         '[boot.util :as util]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

;; this line prevents confusing the deployer with dependencies` pom.xml files
(alter-var-root #'boot.pod/standard-jar-exclusions (constantly (conj boot.pod/standard-jar-exclusions #"/pom\.xml$")))

(def version-ns (symbol "rdf4j.version"))

(deftask encode-version
  "Look for version value inside namespace `version-ns` generated by boot-semver."
  [vn version-ns VER sym "Namespace with 'version value."]
  (let [version-ns (:version-ns *opts*)
        file-ns (str (clojure.string/join "/" (clojure.string/split
                                            (clojure.string/replace (str version-ns) #"-"
                                                                    "_") #"\.")) ".clj")
        project (str (name (get-env :project)))]
    (util/info "Load namespace %s\n" version-ns)
    (core/with-pre-wrap fs
      (load-file (-> (core/by-path (hash-set file-ns) (ls fs))
                     (first)
                     (core/tmp-file)
                     (.getCanonicalPath)))
      (core/commit! (with-meta fs {:version-sem (let [version-sem @(ns-resolve version-ns 'version)]
                                                  (util/info "Version : %s \n" version-sem)
                                                  version-sem)
                                   :project project})))))

(task-options!
 version {:minor 'two :patch 'one :generate version-ns }
 encode-version {:version-ns version-ns}
 pom {:project (get-env :project) :version (get-version)}
 aot {:all true})


(deftask develop
  "Build SNAPSHOT version of jar"
  []
  (version :pre-release 'snapshot :develop true))


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
  "Build without dependencies" []
  (comp
   (pom)
   (aot)
   (jar)
   (target)))

(deftask build-standalone
  "Build standalone version"
  []
  (comp
   (pom)
   (aot)
   (uber)
   (encode-version)
   (jar :file "project.jar")
   ;; Solution found by https://clojurians.slack.com/team/micha  
   (with-pre-wrap fs
     (core/commit! (let [{:keys [version-sem project]} (meta fs)
                         file-name (format "%s-%s-standalone.jar" project version-sem)]
                     (util/info "Write final jar file: %s\n" file-name)
                     (core/mv fs "project.jar" file-name))))
   (target)))

(deftask build-sources
  "Build sources jar file"
  []
  (comp
   (sift :add-resource ["src/"])
   (encode-version)
   (jar :file "project.jar")
   ;; Solution found by https://clojurians.slack.com/team/micha  
   (with-pre-wrap fs
     (core/commit! (let [{:keys [version-sem project]} (meta fs)
                         file-name (format "%s-%s-sources.jar" project version-sem)]
                     (util/info "Write final jar file: %s\n" file-name)
                     (core/mv fs "project.jar" file-name))))
   (target)))
