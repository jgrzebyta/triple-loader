(ns rdf4j.dump
  (:gen-class)
  (:use [clojure.java.io :as io]
        [clojure.string :refer [blank?]]
        [clojure.tools.cli :refer [cli]]
        [clojure.tools.logging :as log]
        [rdf4j.version :refer [version]])
  (:require [rdf4j.core.rio :refer [default-pp-writer-config]]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u])
  (:import [java.io File StringWriter OutputStreamWriter]
           [java.nio.file Paths Path]
           [java.util.function Supplier]
           [org.eclipse.rdf4j.repository.contextaware ContextAwareRepository]
           [org.eclipse.rdf4j.repository.http HTTPRepository]
           [org.eclipse.rdf4j.rio Rio RDFFormat]
           [org.eclipse.rdf4j.rio.trig TriGWriter]))

(defn- file-to-path [file-string]
  (if-not (blank? file-string)
    (u/normalise-path file-string)
    nil))


(def trig-supplier (proxy [Supplier] []
                     (get [] (.get (Rio/getWriterFormatForMIMEType "application/trig")))))


(defn ^:deprecated make-io-writer
  "Prepares Java IO Writer for file (java Path) or STDOUT.

  Replaced by `make-default-output-stream`."
  [^Path file-path]
  {:pre [(or (instance? Path file-path) (nil? file-path))]} ;; Accepts only either instance of Path or nil
  (if (some? file-path)
    (io/writer (.toFile file-path))
    (io/writer (OutputStreamWriter. System/out))))

(defn make-default-output-stream
  "Prepare Java IO OutputStream for file (java Path) or STDOUT."
  [^Path file-path]
  {:pre [(or (instance? Path file-path) (nil? file-path))]} ;; Accepts only either instance of Path or nil
  (if (some? file-path)
    (io/output-stream (.toFile file-path))
    System/out))

(defn make-rdf-writer "Creates `RDFWriter` based on file name or TriGWriter by default."
  [io-writer ^Path out-file]
  {:pre [(or (instance? Path out-file) (nil? out-file))]} ;; accepts only either instace of Path or nil
  (log/debug (format "io-writer type: %s \tout-file: %s" (type io-writer) out-file))
  (let [writer-format (if (some? out-file)
                        (.orElseGet (Rio/getWriterFormatForFileName (.getName (.toFile out-file)))
                                    trig-supplier)
                        (.get trig-supplier))]
    (log/debug (format "writer format: %s\t io-wrtiter: %s" (type writer-format) (type io-writer)))
    (Rio/createWriter writer-format io-writer)))

(defmacro with-rdf-writer
  "binding => rdf-writer out-file.

  Wraps low level java writer together with `RDFWriter`.

  The particular instance of `RDFWriter` depends on the `out-file` extension or it is `TriGWriter` by default.
  If out-file is nil than exeryting is written to the standard output.
  "
  [binds & body]
  (let [[rdf-writer-var out-file] binds
        normalised-path-var (gensym "norm_")]
    `(let [~normalised-path-var (u/normalise-path ~out-file)]
       (with-open [io-os# (make-default-output-stream ~normalised-path-var)]
         (let [~rdf-writer-var (make-rdf-writer io-os# ~normalised-path-var)]
           ~@body
           )
         ))))

(defn- do-dump [opts]
  (let [path (file-to-path (:f opts))]
    (with-open [out-stream (make-default-output-stream path)]
      (let [repository (HTTPRepository. (:s opts) (:r opts))
            rdf-writer (make-rdf-writer out-stream path)]
        (log/info (if (some? path)
                    (format "Output file: %s [%s format]" (.toUri path) (->
                                                                         rdf-writer
                                                                         (.getRDFFormat)
                                                                         (.getName)))
                    (format "standard output [TriG format]")))
        (try 
          (r/with-open-repository [cnx repository]
            (try
              (.begin cnx)
              (.setWriterConfig rdf-writer default-pp-writer-config)
              (.export cnx rdf-writer (r/context-array))
              (finally (log/debug "Finish...")
                       (.commit cnx))))
          (finally (.shutDown repository)))))))


(defn ^{:no-doc true} -main [& args]
  (let [[opts args banner] (cli args
                                ["--help" "-h" "Print this screen" :default false :flag true]
                                ["--server URL" "-s" "RDF4J SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                                ["--repositiry NAME" "-r" "Repository id" :default "test"]
                                ["--file FILE" "-f" "Data file path or standard output if not given" :default ""]
                                ["--version" "-V" "Display program version" :defult false :flag true])]
    (cond
      (:h opts) (println banner)
      (:V opts) (println "Version: " version)
      :else (do-dump opts))
    (shutdown-agents)))
