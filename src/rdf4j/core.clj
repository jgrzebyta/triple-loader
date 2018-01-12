(ns rdf4j.core
  "Collection of different generic multimethods"
  (:import [org.eclipse.rdf4j.model Model]
           [org.eclipse.rdf4j.repository.sail SailRepository]))

(defmulti get-statements
  "[data-src s p o reified context]

  Returns a lazy sequence of statements following pattern: s p o & c.
  Boolean option `reified` is passed to the data source and specifies if the output contains deified RDF statements." (fn [data-src s p o reified c] (class data-src)))

;; Method taken from rdf4j.triples-source.wrappers

(defmulti get-all-statements "Returns a lazy sequence of all statements from different data-sources" (fn [data-src] (type data-src)))

(defmulti ^Model as-model
  "Presents (and converts) a data source as instance of `Model`.

  The final class of `Model` depends on `model-type` option:
  - :memory (`LinkedHashModel`)
  - :persisted (persisted `MemoryStore`)
  - :solid (`NativeStore`)"
  (fn [data-src & {:keys [model-type]}] (type data-src)))

(defmulti ^SailRepository as-repository
  "Presents (and converts) a data source as instance of `SailRepository`.

  Parameters `repository-type`, `data-dir` and `opts` are directly passed to `rdf4j.repository.sails/make-sail-repository`."
  (fn [data-src repository-type data-dir & opts] (type data-src)))
