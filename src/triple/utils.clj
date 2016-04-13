(ns triple.utils
  (:import
   [org.openrdf.model.impl SimpleValueFactory]
   [org.openrdf.repository Repository RepositoryConnection]))


(defn iter-seq "It is iterator-seq like but works with iterator-like patterns.
Reused implementation describe in http://stackoverflow.com/questions/9225948/ task."
  [i] 
    (lazy-seq 
      (when (.hasNext i)
        (cons (.next i) (iter-seq i)))))


(defmulti value-factory "Returns instance of value-factory for given optional object. The object might be either RepositoryConnection or Repository" (fn [& [x]] (type x)))

(defmethod value-factory Repository [x] (.getValueFactory x))
(defmethod value-factory RepositoryConnection [x] (value-factory (.getRepository x)))
(defmethod value-factory :default [& _] (SimpleValueFactory/getInstance))
