(ns rdf4j.models
  (:require [rdf4j.loader :as l]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u])
  (:import [java.io File StringWriter]
           [java.util Collection]
           [org.eclipse.rdf4j.model Model Resource Value IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModel]
           [org.eclipse.rdf4j.model.util Models]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.rio Rio RDFFormat WriterConfig]
           [org.eclipse.rdf4j.rio.helpers BasicWriterSettings]
           [org.eclipse.rdf4j.sail Sail SailConnection]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.model SailModel]
           [org.eclipse.rdf4j.sail.nativerdf NativeStore]))

(defn ^{:added "0.2.2"} single-subjectp
  "Predicate to check if `model` contains single subject."
  {:added "0.2.2"}
  [^Model model]
  (= 1 (+
        (-> (Models/subjectIRIs model)
            (count))
        (-> (Models/subjectBNodes model)
            (count)))))

(defmulti ^{:added "0.2.2"} loaded-model
  "Factory to create instance of `Model` either from `Statement`s collection or from
   a RDF file."
  (fn [data-source & _] (type data-source)))


(defmethod loaded-model Collection [statements-seq & { :keys [sail-type]}]
  (if-let [base-store (case sail-type
                        :memory (doto (MemoryStore.) (.setPersist true))
                        :disk (NativeStore.)
                        nil)]
    (let [initialised-store (doto base-store
                              (r/make-sail-datadir u/temp-dir ["store"])
                              .initialize)
          ^SailConnection sail-conn (.getConnection initialised-store)]
      (doall (map (fn [s]
                    (.addStatement sail-conn (.getSubject s) (.getPredicate s) (.getObject s) (into-array [(.getContext s)]))) statements-seq))
      (loaded-model initialised-store))
    (-> statements-seq
        (LinkedHashModel.))))

(defmethod loaded-model File [statements-file & _]
  (let [repo (r/make-repository)]
    (l/load-data repo statements-file)
    (-> (r/get-all-statements repo)
         (LinkedHashModel.))))

(defmethod loaded-model Sail [statements-sail & _]
  (.initialize statements-sail)
  (SailModel. (.getConnection statements-sail) false))
 
(defmethod loaded-model SailRepository [statements-src & _]
  (.initialize statements-src)
  (SailModel. (-> statements-src
                  .getConnection
                  .getSailConnection) false))

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
