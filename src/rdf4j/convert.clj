(ns rdf4j.convert
  "Delivers convertion service between different formats.
  Output is always to <STDOUT>."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as st]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [rdf4j.dump :as d]
            [rdf4j.loader :as l]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            [rdf4j.version :refer [version]])
  (:import [java.nio.file Path Paths]
           [org.eclipse.rdf4j.model Model]
           [org.eclipse.rdf4j.rio RDFFormat Rio ParserConfig WriterConfig]
           [org.eclipse.rdf4j.rio.helpers ParseErrorLogger BasicParserSettings BasicWriterSettings]))

(def map-formats {"ntriples" RDFFormat/NTRIPLES
                  "n3" RDFFormat/N3
                  "turtle" RDFFormat/TURTLE
                  "rdfjson" RDFFormat/RDFJSON
                  "rdfxml"RDFFormat/RDFXML
                  "trig" RDFFormat/TRIG
                  "trix" RDFFormat/TRIX
                  "nquads" RDFFormat/NQUADS
                  "jsonld" RDFFormat/JSONLD
                  "binary" RDFFormat/BINARY})

(def default-parser-config (doto
                               (ParserConfig.)
                             (.set BasicParserSettings/NORMALIZE_DATATYPE_VALUES true)
                             (.set BasicParserSettings/PRESERVE_BNODE_IDS true)
                             (.set BasicParserSettings/VERIFY_URI_SYNTAX true)))

(def default-pp-writer-config (doto
                                  (WriterConfig.)
                                (.set BasicWriterSettings/PRETTY_PRINT true)
                                (.set BasicWriterSettings/XSD_STRING_TO_PLAIN_LITERAL false)
                                (.set BasicWriterSettings/INLINE_BLANK_NODES true)))

(def ^:private cli-options
  [["-h" "--help" "Print this screen"]
   ["-v" "--version" "Display version"]
   ["-i" "--input FORMAT" "Input format. Available formats: ntriples, n3, turtle, rdfjson, rdfxml, trig, trix, nquads, jsonld, binary" :parse-fn map-formats]
   ["-o" "--output FORMAT" "Output format. Available formats: ntriples, n3, turtle, rdfjson, rdfxml, trig, trix, nquads, jsonld, binary" :parse-fn map-formats]])

(defn- validate-args [args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)
        parsed (cond-> {}
                 (:help options) (conj {:msg [summary] :ok true})
                 (:version options) (conj {:msg [(str "Version: " rdf4j.version/version)] :ok true})
                 errors (conj {:msg errors :ok false})
                 (= 1 (count arguments)) (conj {:source (Paths/get (first arguments) (make-array String 0))})
                 (:input options) (conj {:input (:input options)})
                 (:output options) (conj {:output (:output options)}))]
    (log/tracef "parsed: %s" parsed)
    (cond
      (and (not (parsed :msg))
          (not (parsed :source))) {:msg ["Valid file name is expected"] :ok false}
      :else parsed)))


(defn- make-stdout-rdf-writer [^RDFFormat rdf-format]
  (let [io-wr (d/make-io-writer nil)]
    (log/debugf "IO writer: %s" io-wr)
    (doto
        (Rio/createWriter rdf-format io-wr)
      (.setWriterConfig default-pp-writer-config))))

(defn- ^Model read-file-rdf-model
  "Loads content of the file into new RDF4J Model."
  [^Path file ^RDFFormat rdf-format]
  (let [basename (-> file
                     u/normalise-path
                     .toUri
                     .toString)]
    (Rio/parse (io/reader (.toFile file)) basename rdf-format default-parser-config (u/value-factory) (ParseErrorLogger.) (u/context-array))))

(defn -main [& args]
  (let [val (validate-args args)]
    (log/debugf "Validated: %s" (with-out-str (clojure.pprint/pprint val)))
    (when (val :msg)
      (println (st/join \newline (val :msg)))
      (System/exit (if (val :ok) 0 1)))
    (let [{ :keys [source input output]} val
          model (read-file-rdf-model source input)
          writer (make-stdout-rdf-writer output)]
      (log/debug "Model count: %d" (count model))
      (Rio/write model writer))))
