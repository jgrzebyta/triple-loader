(set-env! :source-paths #{"src"}
          :resource-paths #{"resources"}
          :project 'adalab/triple-loader
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/tools.cli "0.3.5"]
                          [org.clojure/tools.logging "0.3.1"]
                          [clj-pid/clj-pid "0.1.2"]
                          [commons-io/commons-io "2.5"]
                          [org.eclipse.rdf4j/rdf4j-repository-http "2.0M2" :exclusions [commons-io org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-runtime "2.0M2" :exclusions [org.slf4j/slf4j-api]]
                          [org.eclipse.rdf4j/rdf4j-repository-sail "2.0M2" :scope "test" :exclusions [org.slf4j/slf4j-api]]
                          [org.slf4j/jcl-over-slf4j "1.7.21"]
                          [org.apache.logging.log4j/log4j-slf4j-impl "2.5"]
                          [org.apache.logging.log4j/log4j-core "2.5"]])

(require '[boot.core :as c]
         '[clojure.test :as test]
         '[boot.util :as util]
         '[boot.pod :as pod]
         '[clojure.pprint :as pp])


(def +version+ "0.1.3.1")

(task-options!
 pom {:project (get-env :project) :version +version+ }
 aot {:namespace '#{triple.repository triple.loader sparql}})

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

(def ^:private  +add-dependencies+ '[[clj-time/clj-time "0.12.0"]
                                     [boot/core "2.6.0"]])


(defn- create-pod "Taken from https://github.com/hashobject/perun/blob/master/src/io/perun.clj"[]
  (-> (get-env)
      (update-in [:dependencies] into +add-dependencies+)
      pod/make-pod
      future))

(defn- create-pod-mock []
  (-> (get-env)
      (update-in [:dependencies] into +add-dependencies+)))


(deftask write-version
  "Some mock task" [v version NAME str "Version number to save in version.clj file."]
  *opts*
  (let [wrk (create-pod)]
    (pp/pprint +version+)
    (pod/with-eval-in @wrk
      (require '[clojure.pprint :as pp]
               '[boot.core]
               '[boot.git])
      (pp/pprint (:version *opts*))
      )
    )
  )



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
   (jar :file (format "%s-standalone.jar" (name (get-env :project))))
   (target)))
