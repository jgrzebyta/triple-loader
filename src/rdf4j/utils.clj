(ns rdf4j.utils
  (:require [clojure.tools.logging :as log]
            [clj-pid.core :as pid]
            [clojure.string :as str :exclude [reverse]])
  (:import [java.util Map]
           [java.io ByteArrayInputStream BufferedInputStream]
           [java.nio.file Files Path Paths]
           [java.nio.file.attribute FileAttribute]
           [org.eclipse.rdf4j.repository Repository RepositoryConnection]
           [org.eclipse.rdf4j.model.impl SimpleValueFactory LinkedHashModel]
           [org.eclipse.rdf4j.model.util URIUtil]
           [org.eclipse.rdf4j.model Value Resource ValueFactory]
           [org.eclipse.rdf4j.common.xml XMLUtil]))

;;; Utils methods

(defn iter-seq "It is iterator-seq like but works with iterator-like patterns.
Reused implementation describe in http://stackoverflow.com/questions/9225948/ task."
  [i] 
    (lazy-seq 
      (when (.hasNext i)
        (cons (.next i) (iter-seq i)))))


(defmulti ^ValueFactory value-factory "Returns instance of value-factory for given optional object. The object might be either RepositoryConnection or Repository" (fn [& [x]] (type x)))

(defmethod value-factory Repository [x] (.getValueFactory x))
(defmethod value-factory RepositoryConnection [x] (.getValueFactory x))
(defmethod value-factory :default [& _] (SimpleValueFactory/getInstance))

(defn ^{ :added "0.2.2" :static true }
  context-array
"Create array of Resource. 

  Code was adapted from kr-sesame: sesame-context-array.
  "
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


(defn rand-string [^Integer length]
  (let [space "QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm1234567890"]
    (apply str (repeatedly length #(rand-nth space)))))


(defn ^Path temp-dir [^String & namespace]
  "Create temporary directory."
  (let [pid (pid/current)
        ns (if (str/blank? (first namespace)) "loader" (first namespace))
        rand (rand-string 9)
        prefix (str/join nil (list "rdf4j-" ns "-" rand "." pid))]
    (Files/createTempDirectory prefix (make-array FileAttribute 0))))

(defmulti normalise-path
  "Proceeds path string normalisation. Additionally replace '~' character by Java's 'user.home' system property content.
  If string is blank (ref. clojure.string/blank?) than returns nil."
  (fn [path] (type path)))

(defmethod normalise-path String [path]
  (if-not (str/blank? path)
    (normalise-path (Paths/get path (make-array String 0)))
    nil))

(defmethod normalise-path Path [path]
  (let [path-as-string (.toString path)]
    (.normalize (Paths/get (.replaceFirst path-as-string "^~" (System/getProperty "user.home"))
                           (make-array String 0)))))

(defn normalise-path-supportsp
  "Check if type of `data` is suppoerted by `normalise-path`."
  [data]
  (contains? (methods normalise-path) (type data)))


(defn ^Path create-dir
  "Create directory"
  [^String path]
  (let [^Path normalised (normalise-path path)]
    (Files/createDirectory normalised (make-array FileAttribute 0))))

(defn multioption->seq "Function handles multioptions for command line arguments"
  [previous key val]
  (assoc previous key
         (if-let [oldval (get previous key)]
           (merge oldval val)
           (list val))))

(defn re-splitted->seq
  "Function handles options separated by regular expression."
  [re previous key val]
  (let [val-vect (str/split val re)]
    (assoc previous key
           (if-let [oldval (get previous key)]
             (apply list (concat oldval val-vect))
             (apply list val-vect)))))

(defn comma->seq
  [previous key val]
  (apply re-splitted->seq #"," [previous key val]))

(defn string->stream [^String s]
  (if (some? s)
    (BufferedInputStream. (ByteArrayInputStream. (.getBytes s)) 2048)
    nil))

(defn map->binding [^Map m]
  {:pre [(instance? Map m)]}
  (reduce (fn [m [k v]] (assoc m (keyword k) v)) {} m))


(defn ^Value any-to-value
  "Convert anything into `Value`.
  
   The argument value is checked in the following order:
      - if `v` is valid IRI than create result using `(.createIRI value-factory v)`
      - otherwise create `Literal`."
  [v]
  (let [v-string (String/valueOf v)
        vf (value-factory)]
    (cond
      (instance? Value v) v
      (URIUtil/isValidURIReference v-string) (.createIRI vf v-string)
      :else (try (.createLiteral vf v)
                 (catch Exception e
                   (throw (ex-info "Wrong argument type:" {:type (type v)})))))))


(defn string->id
  "Converts free string into id.

   This method does following actions:
     - converts any white characters into single underscore
     - converts pattern `\\s*[,;-_]?\\s+` into single underscore
     - converts all upper-case letters into lower-case
     - process `XMLUtil/escapeText`
  "
  [s]
  (->
   (str/lower-case s)
   (str/replace #"\s*[-_,;]?\s+" "_")
   (XMLUtil/escapeText)))
