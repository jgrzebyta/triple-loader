(ns rdf4j.writer
  (:use [clojure.pprint :as pp]
        [clojure.string :refer [lower-case]])
  (:import [java.io StringWriter]
           [java.util ServiceLoader]
           [org.eclipse.rdf4j.rio Rio RDFWriterFactory]
           [org.eclipse.rdf4j.query.resultio TupleQueryResultWriterFactory]))

(defrecord Writers [name mime extension factory query-type])


(defn writers-from-factory
  [query-type factory]
  (let [format (if (instance? TupleQueryResultWriterFactory factory)
                 (.getTupleQueryResultFormat factory)
                 (.getRDFFormat factory))]
    (->Writers (lower-case (.getName format)) (.getMIMETypes format) (.getFileExtensions format) factory query-type)))


(defn- list-graph-writers
  "Lists all instances of RDFWriterFactory"
  [] (iterator-seq (.iterator (ServiceLoader/load RDFWriterFactory))))

(defn- list-tuple-writers
  [] (iterator-seq (.iterator (ServiceLoader/load TupleQueryResultWriterFactory))))

(defn list-writers-with-features
  "List writers with suppoerted MIME and file extensions."
  ([] (into (list-writers-with-features :graph) (list-writers-with-features :tuple)))
  ([type] (let [typed (partial writers-from-factory type)
               list-writers (case type
                              :graph list-graph-writers
                              :tuple list-tuple-writers)]
           (map typed (list-writers)))))

(defn list-all
  "List writer's names"
  []
  (map #(get %1 :name) (list-writers-with-features)))

(defn contains-writer [name]
  (some? (some #{name} (list-all))))


(defn get-factory-by-name [name]
  (if (contains-writer name)
    (get (first (filter #(= name (get % :name)) (list-writers-with-features))) :factory )
    (throw (ex-info "Writer is either not supported or not exist" {:request-type name}))))

(defn help
  "Print list of all writers as ascii table"
  []
  (let [sw (StringWriter.)]
    (binding [*out* sw]
      (pp/print-table [:name :mime :extension :query-type] (list-writers-with-features)))
    (println (format "\nSupported writers %s\n" (.toString sw)))))

