(ns triple.repository
  (:import [org.openrdf.model.impl SimpleValueFactory]
           [org.openrdf.model Resource IRI Value]
           [org.openrdf.repository RepositoryConnection Repository]
           [org.openrdf.sail Sail]
           [org.openrdf.repository.sail SailRepository]
           [org.openrdf.sail.memory MemoryStore]))


;;; Utils methods

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

;;; End Uils methods




(defn make-repository "Create repository for given store. By default it is MemeoryStore"
  [& [^Sail store]]
  (doto
      (SailRepository. (if store store (MemoryStore.)))
    (.initialize)))


(defn make-mem-repository "Backward compatibility" []
  (make-repository))


(defmacro with-open-repository
  "	initseq = [seq-var :memory] | [seq-var (HTTPRepository. server-url repository-id)] 
  
  Opens connetion CONNECTION-VARIALE to RDF repository.
  
  Where initseq is (CONNECTION-VARIABLE REPOSITORY). For example (cnx :memory)
  If REPOSITORY has value ':memory' then memory repository is created."
  [initseq & body]
  (let [[connection-var repo-init] initseq
        repository-seq# (if (= repo-init :memory)
                     `(make-repository)
                     repo-init)]
    `(let [repository# ~repository-seq#]
       (.initialize repository#)
       (with-open [~connection-var (.getConnection repository#)]
         ~@body)
       (.shutDown repository#))))


(defn context-array
"Create array of Resource. 

Code was adapted from kr-sesame: sesame-context-array."
  ([] (make-array Resource 0))
  ([_] (let [out (make-array Resource 1)]
           (aset out 0 nil)
           out))
  ([kb a] (let [vf (value-factory kb)
                out (make-array Resource 1)]
              (aset out 0 (.createIRI vf a))
              out))
  ([kb a & rest] (let [vf (value-factory kb)
                       out (make-array Resource (inc (count rest)))]
                   (map (fn [i val]
                          (aset out i (.createIRI vf val)))
                        (range)
                        (cons a rest))
                   out))
  )

(defn get-statements [kb s p o use-reified context]
  (.getStatements ^RepositoryConnection kb
                  ^Resource s
                  ^IRI p
                  ^Value o
                  (boolean use-reified)
                  context))



(defn iter-seq "It is iterator-seq like but works with iterator-like patterns.
Reused implementation describe in http://stackoverflow.com/questions/9225948/ task."
  [i] 
    (lazy-seq 
      (when (.hasNext i)
        (cons (.next i) (iter-seq i)))))
