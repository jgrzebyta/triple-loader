(ns rdf4j.collections.utils
  (:require [rdf4j.utils :as u]
            [rdf4j.loader :as l])
  (:import [org.eclipse.rdf4j.model Model]
           [org.eclipse.rdf4j.model.vocabulary RDF RDFS]
           [org.eclipse.rdf4j.model.util Models]))

(defn ^{:added "0.2.2"} collection-type [^Model model]
  (cond
    ;; detect if model contains rdf:first and rdf:rest or is type of rdf:List
    (and
     (.contains model nil RDF/FIRST nil (u/context-array))
     (.contains model nil RDF/REST nil (u/context-array))) RDF/LIST
    (or (.contains model nil RDF/TYPE RDF/ALT (u/context-array))
        (.contains model nil RDF/TYPE RDF/BAG (u/context-array))
        (.contains model nil RDF/TYPE RDF/SEQ (u/context-array))) RDFS/CONTAINER
    :else (throw (ex-info "Collection type unknown" {:rdf-type (-> model
                                                                   (.filter nil RDF/TYPE nil)
                                                                   (Models/objectStrings))}))))

