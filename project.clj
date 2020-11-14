(defproject adalab/triple-loader "0.2.4-SNAPSHOT"
  :description "Triple-loader provides command line and clojure script writing tools for managing RDF data."
  :url "http://example.com/FIXME"
  :license {:name "GNU LESSER GENERAL PUBLIC LICENSE Version 3"
            :url "https://www.gnu.org/licenses/lgpl-3.0.en.html"}
  :min-lein-version "2.9.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
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
                 [org.slf4j/jcl-over-slf4j "1.7.9"]]
  :source-paths ["src/main"]
  :test-paths ["src/test"]
  :profiles {
             :precomp { :source-paths ["src/pre"]
                        :aot [rdf4j.models.located-sail-model] }
             :user { :plugins [[lein-set-version "0.4.1"]]
                    :set-version
                              { :updates [{ :path "src/main/rdf4j/version.clj" }
                                          ]}
                    }
             }
  :aliases {
            "all-compile" ["do" ["with-profile" "precomp" "compile"] ["compile"]]
            }
  :repositories { "sonatype-public" { :url "https://oss.sonatype.org/content/groups/public/" }
                 "central" { :url "https://repo1.maven.org/maven2/"}
                 "clojars" { :url "https://clojars.org/repo/" }
                 }
  :repl-options {:init-ns test-clojure.core})
