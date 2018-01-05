(ns rdf4j.models.located-sail-model
  (:import [java.io File]
           [java.nio.file Path]
           [org.eclipse.rdf4j.sail SailConnection]
           [org.eclipse.rdf4j.repository.sail SailRepository]))


(gen-class
   :name rdf4j.models.LocatedSailModel
   :extends org.eclipse.rdf4j.sail.model.SailModel
   :state state
   :constructors {[org.eclipse.rdf4j.repository.sail.SailRepository Boolean] [org.eclipse.rdf4j.sail.SailConnection boolean]
                  [java.io.File org.eclipse.rdf4j.sail.SailConnection Boolean] [org.eclipse.rdf4j.sail.SailConnection boolean]
                  [java.nio.file.Path org.eclipse.rdf4j.sail.SailConnection Boolean] [org.eclipse.rdf4j.sail.SailConnection boolean]}
   :methods [[setDataDir [java.io.File] void]
             [setDataDir [java.nio.file.Path] void]
             [getDataDir [] java.io.File]]
   :init init)

(defn -init
  ([]
   [[] (atom {:data-dir nil})])
  ([^SailRepository repo inferred]
   (when (not (.isInitialized repo))
     (.initialize repo))
   [[(-> repo
        .getConnection
        .getSailConnection) inferred] (atom {:data-dir (.getDataDir repo)})])
  ([data-dir ^SailConnection conn inferred]
   [[conn inferred] (atom {:data-dir (case (type data-dir)
                                             File data-dir
                                             Path (.toFile data-dir))})]))

(defn -setDataDir-File [this ^File data-dir]
  (reset! (.state this) {:data-dir data-dir}))

(defn -setDataDir-Path [this ^Path data-dir]
  (reset! (.state this) {:data-dir (.toFile data-dir)}))

(defn -getDataDir [this]
  (:data-dir @(.state this)))



