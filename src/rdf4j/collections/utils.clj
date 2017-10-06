(ns rdf4j.collections.utils
  (:require [rdf4j.repository :as r]
            [rdf4j.loader :as l])
  (:import [java.util Collection]
           [org.eclipse.rdf4j.model Model]
           [org.eclipse.rdf4j.model.impl LinkedHashModel]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.model.util Models]))

(defn collection-type [^Model model]
  (cond
    ;; detect if model contains rdf:first and rdf:rest or is type of rdf:List
    (or (and
         (.contains model nil RDF/FIRST nil (r/context-array))
         (.contains model nil RDF/REST nil (r/context-array)))
        (.contains model RDF/TYPE RDF/LIST)) RDF/LIST
    (or (.contains model nil RDF/TYPE RDF/ALT)
        (.contains model nil RDF/TYPE RDF/BAG)
        (.contains model nil RDF/TYPE RDF/SEQ)) RDFS/CONTAINER
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

(defn ^Model loaded-model
  "Factory to create instance of `Model` either from `Statement`s collection or from
   a RDF file."
  {:added "0.2.2"}
  ([^Collection statements-seq]
   (-> statements-seq
       (LinkedHashModel.)))
  ([statements-file format]
   (let [repo (r/make-repository)]
     (l/load-data repo statements-file)
     (-> (r/get-all-statements repo)
         (LinkedHashModel.)))))
