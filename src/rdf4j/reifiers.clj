(ns rdf4j.reifiers
  (:require [clojure.tools.logging :as log]
            [rdf4j.utils :as u])
  (:import org.eclipse.rdf4j.repository.RepositoryConnection
           org.eclipse.rdf4j.repository.util.RDFInserter
           clojure.lang.Atom))

(defn counter-commiter
  "Proxy `RDFInserter`."
  ([^RepositoryConnection connection ^Atom counter] (counter-commiter connection (u/context-array) counter))
  ([^RepositoryConnection connection #^"[Lorg.eclipse.rdf4j.model.Resource;" contexts ^Atom counter]
   {:pre [(instance? Atom counter)]}
   (doto
       (proxy [RDFInserter] [connection]
         (handleStatement [statement] (do
                                        (proxy-super handleStatement statement)
                                        (swap! counter inc) ;; increase value of cnt by 1
                                        (log/tracef "[%d] --statement --" @counter))))
     (.enforceContext contexts))))

