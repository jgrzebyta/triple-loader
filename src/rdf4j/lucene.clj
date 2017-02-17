(ns rdf4j.lucene
  (:import [java.util Properties]
           [java.nio.file Path]
           [org.eclipse.rdf4j.sail.lucene LuceneSail SearchIndex]))


(defn ^SearchIndex make-lucene-index
  "Utility method for producing Luce index using settings"
  [^Properties settings]
  (let [index-class (.getProperty settings LuceneSail/INDEX_CLASS_KEY LuceneSail/DEFAULT_INDEX_CLASS)]
    (doto
        (. (resolve (symbol index-class)) newInstance)
      (.initialize settings))))

(defn ^Path define-lucene-index-path
  "Utility method. Return absolute path of Lucene index directory."
  [^Path p]
  (-> p
      (.resolve "index")
      (.toAbsolutePath)
      (.toString)))

