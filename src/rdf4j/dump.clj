(ns rdf4j.dump
  (:gen-class)
  (:use [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [rdf4j.loader :exclude [-main]]
        [rdf4j.repository]
        [rdf4j.version :refer [get-version]])
  (:import [java.nio.file Paths Path]
           [java.io File StringWriter OutputStreamWriter]
           [org.eclipse.rdf4j.repository.contextaware ContextAwareRepository]
           [org.eclipse.rdf4j.repository.http HTTPRepository]
           [org.eclipse.rdf4j.rio.trig TriGWriter]))


(defn- make-io-writer [out-file]
  (if (some? out-file) (io/writer (.toFile (normalise-path out-file))) (io/writer (OutputStreamWriter. System/out))))



(defn- do-dump [opts]
  (with-open [out-writer (make-io-writer (:f opts))]
    (let [repository (HTTPRepository. (:s opts) (:r opts))
          dump-writer (TriGWriter. out-writer)]
    (try 
      (with-open-repository [cnx repository]
        (try
          (.begin cnx)
          (.export cnx dump-writer (context-array))
          (finally (log/debug "Finish...")
                   (.commit cnx))))
      (finally (.shutDown repository))))))


(defn -main [& args]
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--server URL" "-s" "RDF4J SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                                ["--repositiry NAME" "-r" "Repository id" :default "test"]
                                ["--file FILE" "-f" "Data file path or standard output if not given" :default nil]
                                ["--version" "-V" "Display program version" :defult false :flag true])]
    (cond
      (:h opts) (println banner)
      (:V opts) (println "Version: " (get-version))
      :else (do-dump opts))))
