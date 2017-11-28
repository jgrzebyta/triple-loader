(ns rdf4j.models
  (:require [rdf4j.repository :as r]
            [rdf4j.loader :as l])
  (:import [java.io File]
           [java.util Collection]
           [org.eclipse.rdf4j.model Model Resource Value IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModel]
           [org.eclipse.rdf4j.model.util Models]))

(defn ^{:added "0.2.2"} single-subjectp
  "Predicate to check if `model` contains single subject."
  {:added "0.2.2"}
  [^Model model]
  (= 1 (+
        (-> (Models/subjectIRIs model)
            (count))
        (-> (Models/subjectBNodes model)
            (count)))))

(defmulti ^{:added "0.2.2"} loaded-model
  "Factory to create instance of `Model` either from `Statement`s collection or from
   a RDF file."
  (fn [data-source] (type data-source)))

(defmethod loaded-model Collection [statements-seq]
   (-> statements-seq
       (LinkedHashModel.)))

(defmethod loaded-model File [statements-file]
  (let [repo (r/make-repository)]
    (l/load-data repo statements-file)
    (-> (r/get-all-statements repo)
         (LinkedHashModel.))))

(defn ^{:added "0.2.2"}
  rdf-filter
  "`Model/filter` method wrapper with hinted types."
  ([^Model m ^Resource s ^IRI p ^Value o] (rdf-filter m s p o (r/context-array)))
  ([^Model m ^Resource s ^IRI p ^Value o ^"[Lorg.eclipse.rdf4j.model.Resource;" ns] (.filter m s p o ns)))

(defn ^{:added "0.2.2"}
  get-subject-IRIs
  ([^Model m ^IRI p ^Value o] (get-subject-IRIs m p o (r/context-array)))
  ([^Model m ^IRI p ^Value o ^"[Lorg.eclipse.rdf4j.model.Resource;" ns] (rdf-filter m nil p o ns)))

(defn ^{:added "0.2.2"}
  rdf-filter-object
  "Process Java's equivalent of: `m.filter(s p null null)` -> `Models/object` -> `.orElse null`."
  [^Model m ^Resource s ^IRI p]
  (-> (rdf-filter m s p nil)
      Models/object
      (.orElse nil)))
