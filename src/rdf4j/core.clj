(ns rdf4j.core
  "Collection of different generic multimethods"
  (:import [org.eclipse.rdf4j.model Model IRI]
           [org.eclipse.rdf4j.repository.sail SailRepository]))

(defmulti load-data
  "Loads content of `data-source` into `repository`.
   Currently supported types are: `String` (file path), `Model`, `Path` and `File`."
  {:arglists '([repository data-source & {:keys [rdf-handler context-uri] }])}
  (fn [repository data-source & {:keys [rdf-handler context-uri] }] (type data-source)))

(defmulti get-statements
  "Returns a lazy sequence of statements following pattern: s p o & c.
  Boolean option `reified` is passed to the data source and specifies if the output contains deified RDF statements."
  {:arglists '([data-src s p o reified c])}
  (fn [data-src s p o reified c] (class data-src)))

;; Method taken from rdf4j.triples-source.wrappers

(defmulti get-all-namespaces
  "Return a sequence of all instances of `Namespace` in the repository or repository connection"
  {:arglists '([r])}
  (fn [r] (type r)))

(defmulti as-model
  "Presents (and converts) a data source as instance of `Model`.

  The final class of `Model` depends on `model-type` option:
  - :memory (`LinkedHashModel`)
  - :persistent (persistent `MemoryStore`)
  - :solid (`NativeStore`)"
  {:arglists '([data-src & {:keys [model-type]}])}
  (fn [data-src & {:keys [model-type]}] (type data-src)))

(defmulti as-repository
  "Presents (and converts) a data source as instance of `SailRepository`.

  Parameters `repository-type`, `data-dir` and `opts` are directly passed to `rdf4j.repository.sails/make-sail-repository`."
  {:arglists '([data-src repository-type data-dir & opts])}
  (fn [data-src repository-type data-dir & opts] (type data-src)))

(defmulti rdf-filter-object
  "Extract object from triple [s p o c]"
  {:arglists '([data-src s p]) :added "0.2.2"}
  (fn [data-src s p] (type data-src)))
