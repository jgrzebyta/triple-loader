(ns rdf4j.version
  (:use [clojure.java.io :as io]
        [clojure.pprint :as pp]
        [clojure.string :as str :exclude [replace reverse]])
  (:import [java.util.jar JarFile]
           [java.util Properties]))

(declare get-version get-current-jar find-in-jarfile pom-properties)

(def +version-file+ "/adalab/triple-loader/pom.properties")
(def +version-property+ "version")


(defn- get-current-jar
  ([] (get-current-jar *ns*))
  ([ns]
   (-> (class ns)
       (.getProtectionDomain)
       (.getCodeSource)
       (.getLocation)
       (.getPath))))



(defn get-version []
  (.getProperty (pom-properties (get-current-jar)) +version-property+))


;;;; copy from boot/pod.clj

(defn- find-in-jarfile [jf path]
  (let [entries (->> jf .entries enumeration-seq
                     (filter #(.endsWith (.getName %) path)))]
    (when (< 1 (count entries))
      (throw (Exception. (format "Multiple jar entries match: .*%s" path))))
    (first entries)))

(defn pom-properties
  "Given a path or File jarpath, finds the jar's pom.properties file, reads
  it, and returns the loaded Properties object. An exception is thrown if
  multiple pom.properties files are present in the jar."
  [jarpath]
  (with-open [jarfile (JarFile. (io/file jarpath))
              props   (->> (find-in-jarfile jarfile +version-file+)
                           (.getInputStream jarfile))]
    (doto (Properties.)
      (.load props))))
