(ns rdf4j.repository
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [rdf4j.utils :as u])
  (:import [java.util Properties]
           [java.io File]
           [java.nio.file Files Path]
           [org.apache.commons.io FileUtils]
           [org.eclipse.rdf4j.common.iteration CloseableIteration]
           [org.eclipse.rdf4j.model.impl SimpleValueFactory]
           [org.eclipse.rdf4j.model Resource IRI Value]
           [org.eclipse.rdf4j.repository RepositoryConnection Repository]
           [org.eclipse.rdf4j.sail Sail]
           [org.eclipse.rdf4j.sail.spin SpinSail]
           [org.eclipse.rdf4j.sail.evaluation TupleFunctionEvaluationMode]
           [org.eclipse.rdf4j.sail.inferencer.fc DedupingInferencer ForwardChainingRDFSInferencer]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.lucene LuceneSail]
           [org.eclipse.rdf4j.lucene.spin LuceneSpinSail]))

;; Root of application context
(defrecord RepositoryContext [path active application-context])


(def context (atom (RepositoryContext. nil false nil))) ;; create context variable

(defn- get-tmp-dir
  "Gets or creates temporary directory based on given sail or using alternative fn respectively.

If sail has not null dataDir just returns it. If sail contains null dataDir than creates it, set as dataDir and returns.
If sail is null just generates dataDir and returns. 
"
  [^Sail sail alternative-fn & args]
    (or (when sail (if-let [data-dir (.getDataDir sail)]
                     (.toPath data-dir)
                     (when-let [^Path path (apply alternative-fn args)]
                       (.setDataDir sail (.toFile path))
                       path)))
        (apply alternative-fn args)))

(defn make-repository
"Create repository for given store. By default it is MemoryStore"
  [& [^Sail store]]
  (let [^Path tmp-dir (get-tmp-dir store u/temp-dir)]
    (swap! context assoc :path (.toFile tmp-dir) :active true)
    (SailRepository. (DedupingInferencer. (if store store (MemoryStore. (.toFile tmp-dir)))))))

(defn make-repository-with-lucene
  "Similar to make-repository but adds support for Lucene index. 
  NB: See delete-context."
  [& [^Sail store]]
  (let [^Path tmpDir (get-tmp-dir store u/temp-dir "lucene")
        defStore (ForwardChainingRDFSInferencer. (DedupingInferencer. (if store store (MemoryStore. (.toFile tmpDir)))))
        spin (SpinSail. defStore)
        lucene-spin (doto
                        (LuceneSpinSail. spin)
                      (.setDataDir (.toFile tmpDir)))]
    (swap! context assoc :path (.toFile tmpDir) :active true)   ;; keep tmpDir in global variable 
    (log/debug "Storage path: " (.toAbsolutePath tmpDir))
    (SailRepository. lucene-spin)))

(defn make-mem-repository
  "Backward compatibility"
  []
  (make-repository))


(defmacro with-open-repository
  "initseq => [seq-var :memory] | [seq-var (HTTPRepository. server-url repository-id)] 
  
  Opens connetion CONNECTION-VARIABLE to RDF repository.
  
  Where initseq is (CONNECTION-VARIABLE REPOSITORY). For example (cnx :memory)
  If REPOSITORY has value ':memory' then memory repository is created."
  [initseq & body]
  (let [[connection-var repo-init] initseq]
    `(let [^org.eclipse.rdf4j.repository.Repository repository# ~repo-init]
       (log/trace (format "repository instance: %s" (.toString repository#)))
       (try (when-not (.isInitialized repository#)
              (log/debug "Initialize repository")
              (.initialize repository#))
            (with-open [~connection-var (.getConnection repository#)]
              (try
                ~@body
                (catch Exception e#
                  (.rollback ~connection-var)
                  (throw e#))))
            (catch Exception e# (log/error (format "Initialise error [%s]: %s"
                                                   (.getName (.getClass e#))
                                                   (.getMessage e#)))
                   (throw e#))))))


(defn context-array
"Create array of Resource. 

Code was adapted from kr-sesame: sesame-context-array."
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

(defmulti get-statements "Return a sequence of all statements either from repository or repository connection meeting given pattern. " (fn [r s p o use-reified context] (type r)))

(defmethod get-statements Repository [rep s p o use-reified context] (with-open-repository [cnx rep]
                                                                       (get-statements cnx s p o use-reified context)))

(defmethod get-statements RepositoryConnection [kb s p o use-reified context] (doall
                                                                               (u/iter-seq (.getStatements kb
                                                                                                           ^Resource s
                                                                                                           ^IRI p
                                                                                                           ^Value o
                                                                                                           (boolean use-reified)
                                                                                                           context))))

(defn get-all-statements [r]
  (get-statements r nil nil nil false (context-array)))


(defn- deactive [ctx] (assoc ctx :active false))

;; TODO: in the future both functions: make-repository-with-lucene and delete-context
;; should be wrapped within a macro.
(defn delete-context
  "Delete temporary directory with content and close Lucene index

  That method should be called manually somewhere at the end of code.
  " []
  (when (:active @context) ;; process repository deletion only when context is active
    (try
      (when-let [dir (:path @context)]
        (FileUtils/deleteDirectory dir)) ;; commons-io supports deleting directory with contents)
      (finally (swap! context deactive)))))
