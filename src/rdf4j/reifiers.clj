(ns rdf4j.reifiers
  (:require [clojure.tools.logging :as log])
  (:import org.eclipse.rdf4j.repository.RepositoryConnection
           org.eclipse.rdf4j.repository.util.RDFInserter))

(defn counter-commiter "Implements RDFHandler. It accepts 2 arguments "
  [^RepositoryConnection connection counter]
  {:pre [(instance? clojure.lang.Atom counter)]}
  (proxy [RDFInserter] [connection]
    (handleStatement [statement] (do
                                   (proxy-super handleStatement statement)
                                   (swap! counter inc) ;; increase value of cnt by 1
                                   (log/tracef "[%d] --statement --" @counter)))))

