(ns rdf4j.utils
  (:require [clojure.tools.logging :as log]
            [clj-pid.core :as pid]
            [clojure.string :as str :exclude [reverse replace]])
  (:import [java.nio.file Files Path]
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
