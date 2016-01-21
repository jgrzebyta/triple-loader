
(def sesame-version "4.0.1")
(def maven-central ["central1" "http://repo1.maven.org/maven2/"])


(set-env! :source-paths #{"src"}
          :project 'adalab/triple-loader
          :version "0.0.1-SNAPSHOT"
          :dependencies '[[org.clojure/clojure "1.7.0"]
                          [org.clojure/tools.cli "0.3.3"]
                          [org.openrdf.sesame/sesame-repository-api "4.0.1"]]
          :repositories #(conj % maven-central))


(task-options!
 pom {:project (get-env :project) :version (get-env :version)}
 aot { :namespace '#{triple.loadder} }
 jar { :main 'triple.loadder })

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
