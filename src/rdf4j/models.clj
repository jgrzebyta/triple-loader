(ns rdf4j.models
  (:require [clojure.java.io :as io]
            [rdf4j.core :as c]
            [rdf4j.core.rio :as rio]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u])
  (:import [java.io File StringWriter]
           java.nio.file.Path
           java.util.Collection
           [org.eclipse.rdf4j.model IRI Model Resource Value]
           org.eclipse.rdf4j.model.impl.LinkedHashModel
           org.eclipse.rdf4j.model.util.Models
           org.eclipse.rdf4j.repository.sail.SailRepository
           [org.eclipse.rdf4j.rio RDFFormat Rio WriterConfig]
           [org.eclipse.rdf4j.rio.helpers BasicWriterSettings ContextStatementCollector ParseErrorLogger]
           org.eclipse.rdf4j.sail.memory.MemoryStore
           org.eclipse.rdf4j.sail.nativerdf.NativeStore
           rdf4j.models.LocatedSailModel))

(defn ^{:added "0.2.2"} single-subjectp
  "Predicate to check if `model` contains single subject."
  {:added "0.2.2"}
  [^Model model]
  (= 1 (+
        (-> (Models/subjectIRIs model)
            (count))
        (-> (Models/subjectBNodes model)
            (count)))))

(defn ^{:added "0.2.2"}
  rdf-filter
  "`Model/filter` method wrapper with hinted types."
  ([^Model m ^Resource s ^IRI p ^Value o] (rdf-filter m s p o (r/context-array)))
  ([^Model m ^Resource s ^IRI p ^Value o ^"[Lorg.eclipse.rdf4j.model.Resource;" ns] (.filter m s p o ns)))

(defn ^{:added "0.2.2"}
  get-subject-IRIs
  ([^Model m ^IRI p ^Value o] (get-subject-IRIs m p o (r/context-array)))
  ([^Model m ^IRI p ^Value o ^"[Lorg.eclipse.rdf4j.model.Resource;" ns] (rdf-filter m nil p o ns)))

(defn ^{:added "0.2.2"}
  rdf-filter-object
  "Process Java's equivalent of: `m.filter(s p null null)` -> `Models/object` -> `.orElse null`."
  [^Model m ^Resource s ^IRI p]
  (-> (rdf-filter m s p nil)
      Models/object
      (.orElse nil)))

(defn ^{:added "0.2.2"}
  print-model
  "Print a model"
  ([^Model m ^RDFFormat format]
   (let [config (doto (WriterConfig.)
                  (.set BasicWriterSettings/PRETTY_PRINT true)
                  (.set BasicWriterSettings/XSD_STRING_TO_PLAIN_LITERAL false)
                  (.set BasicWriterSettings/INLINE_BLANK_NODES true))]
     (print-model m format config)))
  ([^Model m ^RDFFormat format ^WriterConfig wrt-conf]
   (let [string-writer (StringWriter.)]
     (Rio/write m string-writer format wrt-conf)
     (.toString string-writer))))


(defmethod c/get-statements Model [data-src s p o _ contexts]
  (.filter data-src s p o contexts))

(defn- parse-model-type
  "Util method produces model-type based on the key word."
  [model-type]
  (if (= model-type :memory)
    (LinkedHashModel.)
    (let [ data-dir (->
                     (u/temp-dir "sail-model")
                     .toFile)
          sail-inst (case model-type
                      :solid (NativeStore.)
                      :persistent (MemoryStore.))
          sail-repo (SailRepository. sail-inst)]
     (when (= model-type :persistent)
       (.setPersist sail-inst true))
     (.setDataDir sail-repo data-dir)
     (LocatedSailModel. sail-repo false))))

(defmethod c/as-model nil [_ {:keys [model-type] :or {model-type :memory}}]
  (parse-model-type model-type))

(defmethod c/as-model SailRepository [data-src & {:keys [model-type]}]
  (LocatedSailModel. data-src false))

(defmethod c/as-model Path [data-src & {:keys [model-type] :or {model-type :solid}}]
  (let [normalised (u/normalise-path data-src)]
    (c/as-model (.toFile normalised) :model-type model-type)))

(defmethod c/as-model java.util.Collection [data-src & {:keys [model-type]}]
  (let [model (if (some? model-type)
                (parse-model-type model-type)
                (if (> (count data-src) 1000)
                  (parse-model-type :solid) (parse-model-type :memory)))]
    (.addAll model data-src)
    model))

(defmethod c/as-model File [data-src & {:keys [model-type] :or {model-type :solid}}]
  (let [model (parse-model-type model-type)
        rdf-format (-> (Rio/getParserFormatForFileName (.getPath data-src))
                       .get)
        collector (ContextStatementCollector. model (u/value-factory) (u/context-array))
        parser (doto
                   (Rio/createParser rdf-format (u/value-factory))
                 (.setRDFHandler collector)
                 (.setParserConfig rio/default-parser-config)
                 (.setParseErrorListener (ParseErrorLogger.)))]
    (with-open [ins (io/input-stream data-src)]
      (.parse parser ins (.stringValue (u/make-baseuri (.toPath data-src)))))
    model))


