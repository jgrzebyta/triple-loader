(ns triple.repository
  (:use [clojure.tools.logging :as log]
        [clojure.java.io :as io]
        [clj-pid.core :as pid]
        [clojure.string :as str :exclude [reverse replace]])
  (:import [java.io File]
           [java.nio.file Files Path]
           [java.nio.file.attribute FileAttribute]
           [org.apache.commons.io FileUtils]
           [org.eclipse.rdf4j.common.iteration CloseableIteration]
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


(defn- temp-dir ^Path [^String & namespace]
  "Create temporary directory."
  (let [pid (pid/current)
        ns (if (str/blank? (first namespace)) "loader" (first namespace))
        rand (rand-string 9)
        prefix (str/join nil (list "rdf4j-" ns "-" rand "." pid))]
    (Files/createTempDirectory prefix (make-array FileAttribute 0))))


;;; End Uils methods



(defn make-repository "Create repository for given store. By default it is MemeoryStore"
  [& [^Sail store]]
  (SailRepository. (if store store (MemoryStore.))))

(defn make-repository-with-lucene
  "Similar to make-repository but adds support for Lucene index. 
  NB: See delete-temp-repository."
  [& [^Sail store]]
  (let [tmpDir (.toFile (temp-dir))
        defStore (if store store (MemoryStore. tmpDir))
        luceneSail (LuceneSail.)]
    (def temp-repository (atom { :path tmpDir :active true}))   ;; keep tmpDir in global variable 
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

(defmulti get-statements "Return all statements either from repository or repository connection meeting given pattern. " (fn [r s p o use-reified context] (type r)))

(defmethod get-statements Repository [r s p o use-reified context] (with-open-repository [i r]
                                                                     (doall (lazy-seq (get-statements i s p o use-reified context)))))
(defmethod get-statements RepositoryConnection [kb s p o use-reified context] (.getStatements kb
                                                                                              ^Resource s
                                                                                              ^IRI p
                                                                                              ^Value o
                                                                                              (boolean use-reified)
                                                                                              context))


;; TODO: in the future both functions: make-repository-with-lucene and delete-temp-repository
;; should be wrapped within a macro.
(defn delete-temp-repository
"Delete temporary directory with content.

That method should be called manually somewhere at the end of code.
" []
  (try
    (FileUtils/deleteDirectory (@temp-repository :path)) ;; commons-io supports deleting directory with contents
    (finally (swap! temp-repository assoc :active false))))
