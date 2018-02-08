(ns rdf4j.repository
  (:require [clojure.tools.logging :as log]
            [rdf4j.core :as c]
            [rdf4j.utils :as u]
            [rdf4j.repository :as r]
            [rdf4j.repository.sails :as s])
  (:import java.nio.file.Path
           java.util.Properties
           org.apache.commons.io.FileUtils
           org.eclipse.rdf4j.common.iteration.CloseableIteration
           org.eclipse.rdf4j.lucene.spin.LuceneSpinSail
           [org.eclipse.rdf4j.model IRI Model Resource Value]
           [org.eclipse.rdf4j.repository Repository RepositoryConnection]
           [org.eclipse.rdf4j.repository.sail SailRepository SailRepositoryConnection]
           [org.eclipse.rdf4j.sail.inferencer.fc DedupingInferencer ForwardChainingRDFSInferencer]
           org.eclipse.rdf4j.sail.memory.MemoryStore
           org.eclipse.rdf4j.sail.Sail
           org.eclipse.rdf4j.sail.spin.SpinSail))

;; Root of application context
(defrecord ^:deprecated RepositoryContext [path active application-context])

(def ^:deprecated context (atom (RepositoryContext. nil false nil))) ;; create context variable


(defn ^:deprecated make-sail-datadir
  "Gets or creates data directory.
  
  If sail has set dataDir than just returns it. If sail contains no dataDir than creates it by `(apply alternative-fn args)`, set as dataDir and returns."
  [^Sail sail alternative-fn & args]
    (or (when sail (if-let [data-dir (.getDataDir sail)]
                     (.toPath data-dir)
                     (when-let [^Path path (apply alternative-fn args)]
                       (.setDataDir sail (.toFile path))
                       path)))
        (apply alternative-fn args)))


(defn ^:deprecated
  make-repository
  "Create repository for given store. By default it is MemoryStore.

  Replaced by `rdf4j.repository.sail/make-sail-repository`."
  [& [^Sail store]]
    (SailRepository. (DedupingInferencer. (if store store (MemoryStore.)))))

(defn ^:deprecated
  make-repository-with-lucene
  "Similar to make-repository but adds support for Lucene index. 
  NB: See delete-context.

  Replaced by `rdf4j.repository.sail/make-sail-repository`."
  [& [^Sail store ^Properties parameters]]
  (let [^Path tmpDir (u/temp-dir "lucene")
        local-store (if store store (MemoryStore.))
        defStore (ForwardChainingRDFSInferencer. (DedupingInferencer. local-store))
        spin (SpinSail. defStore)
        lucene-spin (when-let [ls (LuceneSpinSail. spin)]
                      (when (some? parameters) (.addAbsentParameters ls parameters))
                      (.setDataDir ls (.toFile tmpDir))
                      ls)]
    (log/debug "Storage path: " (.toAbsolutePath tmpDir))
    (log/debug "defStore basesail: " (type defStore))
    (log/debug "spin sail: " (type spin))
    (log/debug "lucene spin: " lucene-spin)
    (swap! context assoc :path (.toFile tmpDir) :active true)   ;; keep tmpDir in global variable 
    (SailRepository. lucene-spin)))

(defn ^:deprecated
  make-mem-repository
  "Backward compatibility.

  Replaced by `rdf4j.repository.sail/make-sail-repository`."
  []
  (make-repository))

(defmacro with-open-repository
  "initseq => [seq-var :memory] | [seq-var (HTTPRepository. server-url repository-id)] 
  
  Pesents opened connetion to RDF repository through CONNECTION-VARIABLE. Not transaction aware.
  
  Where initseq is (CONNECTION-VARIABLE REPOSITORY). For example (cnx :memory)
  If REPOSITORY has value ':memory' then memory repository is created.  
  "
  [initseq & body]
  (let [[connection-var repo-init] initseq]
    `(let [^org.eclipse.rdf4j.repository.Repository repository# ~repo-init]
       (log/trace (format "repository instance: %s" (.toString repository#)))
       (try (when-not (.isInitialized repository#)
              (log/debug "Initialize repository")
              (.initialize repository#))
            (catch Exception e# (log/errorf "Initialise error [%s]: %s"
                                            (.getName (.getClass e#))
                                            (.getMessage e#))
                   (throw e#)))
       (with-open [~connection-var (.getConnection repository#)]
         ~@body))))


(defmacro with-open-repository*
  "Transaction aware version of with-open-repository.

  It is requivalent of:

     (with-open-repository [cnx sail-repo]
       (try
          (.begin cnx)
          ~@body
          (catch Exception e
                   (.rollback connection)
                   (throw e))
       (finally .commit cnx)))"
  [initseq & body]
  (let [[rep-connection sail-repo] initseq]
    `(with-open-repository [~rep-connection ~sail-repo]
       (try
         (.begin ~rep-connection)
         ~@body
         (catch Exception e#
           (.rollback ~rep-connection)
           (throw e#))
         (finally (.commit ~rep-connection))))))


(defn ^{ :deprecated true } context-array
"Create array of Resource. 

  Code was adapted from kr-sesame: sesame-context-array.

  DEPRECATED: Use `rdf4j.utils/context-array` instead.
  "
  ([] (make-array Resource 0))
  ([_] (let [out (make-array Resource 1)]
           (aset out 0 nil)
           out))
  ([kb a] (let [vf (u/value-factory kb)
                out (make-array Resource 1)]
              (aset out 0 (.createIRI vf a))
              out))
  ([kb a & rest] (let [vf (u/value-factory kb)
                       out (make-array Resource (inc (count rest)))]
                   (map (fn [i val]
                          (aset out i (.createIRI vf val)))
                        (range)
                        (cons a rest))
                   out)))

(defn count-items "Counts statements in SPARQL result." [^CloseableIteration result]
  (try
    (loop [count 0]
      (if (.hasNext result)
        (do
          (.next result)  ;; ignore result
          (recur (inc count)))
        count))
    (catch Exception e (log/error "Some error: " (.getMessage e)))
    (finally (.close result))))

(defmethod c/get-all-namespaces RepositoryConnection [r] (doall (u/iter-seq (.getNamespaces r))))

(defmethod c/get-all-namespaces Repository [r] (with-open-repository [cn r]
                                               (c/get-all-namespaces cn)))


(defn- deactive [ctx] (assoc ctx :active false))

;; TODO: in the future both functions: make-repository-with-lucene and delete-context
;; should be wrapped within a macro.
(defn ^:deprecated
  delete-context
  "Delete temporary directory with content and close Lucene index.
  If `data-dir` is given directry than delete it.

  That method should be called manually somewhere at the end of code."
  ([]
   (log/debugf "is context active: %s" (:active @context))
   (when (:active @context) ;; process repository deletion only when context is active
     (try
       (when-let [dir (:path @context)]
         (log/debugf "delete files at: %s" dir)
         (FileUtils/deleteDirectory dir)) ;; commons-io supports deleting directory with contents)
       (finally (swap! context deactive)))))
  ([data-dir]
   (let [file-data-dir (if (instance? Path data-dir)
                         (.toFile data-dir)
                         data-dir)]
     (FileUtils/deleteDirectory file-data-dir))))

;; Implementation from rdf4j.core

(defmethod c/as-repository Iterable [data-src repository-type data-dir & opts]
  (let [repository (s/make-sail-repository repository-type data-dir opts)]
    (.initialize repository)
    (c/load-data repository data-src)))

(defmethod c/get-statements SailRepository
  [rep ^Resource s ^IRI p ^Value o use-reified ^"[Lorg.eclipse.rdf4j.model.Resource;" context]
  (with-open-repository [cnx rep]
    (c/get-statements cnx s p o use-reified context)))

(defmethod c/get-statements SailRepositoryConnection
  [kb ^Resource s ^IRI p ^Value o use-reified context]
  (doall
   (u/iter-seq (.getStatements kb s p o use-reified context))))

(defmethod c/rdf-filter-object SailRepository
  [data-src s p]
  (if-let [statement (first (c/get-statements data-src s p nil false (u/context-array)))]
    (.getObject statement) nil))
