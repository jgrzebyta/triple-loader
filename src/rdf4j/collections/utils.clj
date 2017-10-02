(ns rdf4j.collections.utils
  (:require [rdf4j.repository :as r])
  (:import [org.eclipse.rdf4j.model Model]
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
