(ns rdf4j.collections.utils
  (:require [rdf4j.repository :as r]
            [rdf4j.loader :as l])
  (:import [java.io File]
           [java.util Collection]
           [org.eclipse.rdf4j.model Model Resource Value IRI]
           [org.eclipse.rdf4j.model.impl LinkedHashModel]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.model.util Models]))

(defn collection-type [^Model model]
  (cond
    ;; detect if model contains rdf:first and rdf:rest or is type of rdf:List
    (or (and
         (.contains model nil RDF/FIRST nil (r/context-array))
         (.contains model nil RDF/REST nil (r/context-array)))
        (.contains model nil RDF/TYPE RDF/LIST (r/context-array))) RDF/LIST
    (or (.contains model nil RDF/TYPE RDF/ALT (r/context-array))
        (.contains model nil RDF/TYPE RDF/BAG (r/context-array))
        (.contains model nil RDF/TYPE RDF/SEQ (r/context-array))) RDFS/CONTAINER
    :else (throw (ex-info "Collection type unknown" {:rdf-type (-> model
                                                                   (.filter nil RDF/TYPE nil)
                                                                   (Models/objectStrings))}))))

(defn single-subjectp
  "Predicate to check if `model` contains single subject."
  {:added "0.2.2"}
  [^Model model]
  (= 1 (+
        (-> (Models/subjectIRIs model)
            (count))
        (-> (Models/subjectBNodes model)
            (count)))))

(defmulti loaded-model
  "Factory to create instance of `Model` either from `Statement`s collection or from
   a RDF file."
  {:added "0.2.2"}
  (fn [data-source] (type data-source)))

(defmethod loaded-model Collection [statements-seq]
   (-> statements-seq
       (LinkedHashModel.)))

(defmethod loaded-model File [statements-file]
  (let [repo (r/make-repository)]
    (l/load-data repo statements-file)
    (-> (r/get-all-statements repo)
         (LinkedHashModel.))))

(defn rdf-filter
  "`Model/filter` method wrapper with hinted types."
  ([^Model m ^Resource s ^IRI p ^Value o] (rdf-filter m s p o (r/context-array)))
  ([^Model m ^Resource s ^IRI p ^Value o ^"[Lorg.eclipse.rdf4j.model.Resource;" ns] (.filter m s p o ns)))

(defn get-subject-IRIs
  ([^Model m ^IRI p ^Value o] (get-subject-IRIs m p o (r/context-array)))
  ([^Model m ^IRI p ^Value o ^"[Lorg.eclipse.rdf4j.model.Resource;" ns] (filter m nil p o ns)))
