(ns triple.repository
  (:import [org.openrdf.sail Sail]
           [org.openrdf.repository.sail SailRepository]
           [org.openrdf.sail.memory MemoryStore]))


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
