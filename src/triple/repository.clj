(ns triple.repository
  (:use [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [clj-pid.core :as pid]
        [clojure.string :as str :exclude [reverse replace]])
  (:import [java.io File]
           [org.eclipse.rdf4j.model.impl SimpleValueFactory]
           [org.eclipse.rdf4j.model Resource IRI Value]
           [org.eclipse.rdf4j.repository RepositoryConnection Repository]
           [org.eclipse.rdf4j.sail Sail]
           [org.eclipse.rdf4j.repository.sail SailRepository]
           [org.eclipse.rdf4j.sail.memory MemoryStore]
           [org.eclipse.rdf4j.sail.lucene LuceneSail]))


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


(defn rand-string [^Integer length]
  (let [space "QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm1234567890"]
    (apply str (repeatedly length #(rand-nth space)))))


(defn temp-dir [^String & namespace]
  "Create temporary directory."
  (let [root (System/getProperty "java.io.tmpdir")
        pid (pid/current)
        ns (if (str/blank? (first namespace)) "loader" (first namespace))
        rand (rand-string 9)
        separator (File/separator)]
    (str/join separator (list root (str/join nil (list "rdf4j-" ns "-" rand "." pid))))))


;;; End Uils methods



(defn make-repository "Create repository for given store. By default it is MemeoryStore"
  [& [^Sail store]]
  (SailRepository. (if store store (MemoryStore.))))

(defn make-repository-with-lucene "Similar to make-repository but adds support for Lucene index."
  [& [^Sail store]]
  (let [tmpDir (io/file (temp-dir))
        defStore (if store store (MemoryStore. tmpDir))
        luceneSail (LuceneSail.)]
    (.deleteOnExit tmpDir)
    (if (nil? (.getDataDir defStore))
      (.setParameter luceneSail LuceneSail/LUCENE_RAMDIR_KEY "true")
      (.setParameter luceneSail LuceneSail/LUCENE_DIR_KEY (str/join (File/separator) (list tmpDir "lucenedir"))))
    (log/debug "Storage path: " (.getAbsolutePath tmpDir))
    (.setBaseSail luceneSail defStore)
    (make-repository luceneSail)))

(defn make-mem-repository "Backward compatibility" []
  (make-repository))


(defmacro with-open-repository
  "	initseq = [seq-var :memory] | [seq-var (HTTPRepository. server-url repository-id)] 
  
  Opens connetion CONNECTION-VARIABLE to RDF repository.
  
  Where initseq is (CONNECTION-VARIABLE REPOSITORY). For example (cnx :memory)
  If REPOSITORY has value ':memory' then memory repository is created."
  [initseq & body]
  (let [[connection-var repo-init] initseq
        repository-seq# (if (= repo-init :memory)
                     `(make-repository)
                     repo-init)]
    `(let [^org.eclipse.rdf4j.repository.Repository repository# ~repository-seq#]
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

