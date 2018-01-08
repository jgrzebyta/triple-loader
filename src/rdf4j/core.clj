(ns rdf4j.core
  "Colelction of different generic multimethods")

(defmulti get-statements
  "[data-src s p o reified & context]

  Returns a lazy sequence of statements following pattern: s p o & c.
  Boolean option `reified` is passed to the data source and specifies if the output contains deified RDF statements." (fn [data-src s p o reified & c] (type data-src)))

;; Method taken from rdf4j.triples-source.wrappers

(defmulti get-all-statements "Returns a lazy sequence of all statements from different data-sources" (fn [data-src] (type data-src)))

(defmulti as-model "Presents (and converts) a data source as instance of `Model`." (fn [data-src & args] (type data-src)))

(defmulti as-repository "Presents (and converts) a data source as instance of `Repositiry`." (fn [data-src & args] (type data-src)) )
