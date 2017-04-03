(ns rdf4j.utils
  (:require [clojure.tools.logging :as log]
            [clj-pid.core :as pid]
            [clojure.string :refer [blank?]]
            [clojure.string :as str :exclude [reverse replace]])
  (:import [java.nio.file Files Path Paths]
           [java.nio.file.attribute FileAttribute]
           [org.eclipse.rdf4j.repository Repository RepositoryConnection]
           [org.eclipse.rdf4j.model.impl SimpleValueFactory]))

;;; Utils methods

(defn iter-seq "It is iterator-seq like but works with iterator-like patterns.
Reused implementation describe in http://stackoverflow.com/questions/9225948/ task."
  [i] 
    (lazy-seq 
      (when (.hasNext i)
        (cons (.next i) (iter-seq i)))))


(defmulti value-factory "Returns instance of value-factory for given optional object. The object might be either RepositoryConnection or Repository" (fn [& [x]] (type x)))

(defmethod value-factory Repository [x] (.getValueFactory x))
(defmethod value-factory RepositoryConnection [x] (.getValueFactory x))
(defmethod value-factory :default [& _] (SimpleValueFactory/getInstance))


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
  (if-not (blank? path)
    (normalise-path (Paths/get path (make-array String 0)))
    nil))

(defmethod normalise-path Path [path]
  (let [path-as-string (.toString path)]
    (.normalize (Paths/get (.replaceFirst path-as-string "^~" (System/getProperty "user.home"))
                           (make-array String 0)))))

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
