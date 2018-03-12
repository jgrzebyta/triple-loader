(ns rdf4j.loader
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.stacktrace :as cst]
            [clojure.string :as str]
            [clojure.tools.cli :refer :all]
            [clojure.tools.logging :as log]
            [rdf4j.core :as cor]
            [rdf4j.core.rio :as rio]
            [rdf4j.reifiers :as rei]
            [rdf4j.repository :as r]
            [rdf4j.utils :as u]
            rdf4j.version)
  (:import clojure.lang.ExceptionInfo
           java.io.File
           java.nio.file.Path
           [org.eclipse.rdf4j.repository RepositoryConnection RepositoryException]
           org.eclipse.rdf4j.repository.http.HTTPRepository
           org.eclipse.rdf4j.rio.Rio))

(declare load-multidata)

(defn init-connection "Initialise Connection." [^RepositoryConnection connection]
  (log/trace "connection instance: " connection)
  (let [repository (.getRepository connection)]
    (log/debug "Repository: " (if (instance? HTTPRepository repository)
                                (.getRepositoryURL repository)
                                (.toString repository)))
    (try
      (.setParserConfig connection rio/default-parser-config)
      (catch RepositoryException e (do
                                     (log/error (format "Error message: %s" (.getMessage e)))
                                     (throw e)))
      (catch Error t (do
                       (log/error "Error: " (.getMessage t))
                       (System/exit -1))))))

(defn- do-loading [opts]
  ;; validate options
  (assert (and (some? (:s opts)) (some? (:r opts))) "Either server or repository is not given")
  (assert (and (not (str/blank? (:s opts))) (not (str/blank? (:r opts)))) "Either server or repository is not given")
  (log/info (format "Triple store server: [%s] repository name: [%s]" (:s opts) (:r opts)))
  (let [repository (HTTPRepository. (:s opts) (:r opts))
        context-string ((fn [x] (if (or (= x "nil")                        ; convert "nil" and "null" texts into boolean nil
                                        (= x "null")
                                        (= x ""))
                                  nil x)) (:c opts))]
    (log/debug (format "Context string: '%s' is nil '%s'"
                       context-string (nil? context-string)))
    (try
      (let [counted
            (load-multidata repository (:f opts) :context-uri context-string)]
        (log/infof "Loaded %d statements" counted))
      (catch ExceptionInfo e (let [f (get (ex-data e) :file)]
                               (log/errorf "Error during loading file '%s'" f)
                               (System/exit -1)))
      (finally (.shutDown repository)))))

(defmethod cor/load-data Iterable [repository model & {:keys [rdf-handler context-uri] :or {context-uri (u/context-array)}}]
  (r/with-open-repository* [cnx repository]
    (.add cnx model context-uri))
  (count model))

(defmethod cor/load-data String [repository file & {:keys [rdf-handler context-uri]}]
  (cor/load-data repository (u/normalise-path file) :rdf-handler rdf-handler :context-uri context-uri))

(defmethod cor/load-data Path [repository file & {:keys [rdf-handler context-uri]}]
  (cor/load-data repository (.toFile file) :rdf-handler rdf-handler :context-uri context-uri))

(defmethod cor/load-data File [repository file & {:keys [rdf-handler context-uri]}]
  (let [file-reader (io/reader file)
        parser-format (.get (Rio/getParserFormatForFileName (.getName file)))
        parser (Rio/createParser parser-format)
        rdf-handler (or rdf-handler rei/counter-commiter)]
    (log/tracef "File data: %s [exists: %s, readable: %s]"
                (.getPath file)
                (.exists file)
                (.canRead file))
    (log/tracef "data type: %s" parser-format)
    (r/with-open-repository [cnx repository]
      (.setParserConfig cnx rio/default-parser-config)
      (let [counter (atom 0)
            rdf-handler-object (if (fn? rdf-handler)
                                 (apply rdf-handler (if context-uri
                                                      [cnx (u/context-array nil context-uri) counter]
                                                      [cnx counter]))
                                 (apply (resolve rdf-handler) [cnx counter]))]
        (log/debugf "Set up rdf handler: %s" rdf-handler-object)
        (.setRDFHandler parser rdf-handler-object)

        (try
          ;; run parsing
          (.begin cnx) ;; begin transaction
          (log/trace "Isolation level: " (.getIsolationLevel cnx)) ;; this features returns null for SailRepositoryConnection
          
          (.parse parser file-reader (.toString (.toURI file)))

          (catch Exception e
            (.rollback cnx)
            (log/debugf "Stack trace: \n%s" (with-out-str (cst/print-stack-trace e)))
            (throw (ex-info (format "Erorr '%s' occured when loaded file '%s'" (.getMessage e) (.getCanonicalPath file))
                            {:error e
                             :file (.getAbsolutePath file)
                             :message (format "Erorr '%s' occured when loaded file '%s'" (.getMessage e) (.getCanonicalPath file))}
                            e)))
          (finally (.commit cnx)))
        (log/debug "finish ...")
        @counter))))

(defn load-multidata
  "Load multiple data files into repository.

  Returns sum of all statements sent to the repository."
  [repository data-col & { :keys [rdf-handler context-uri]}]
  (assert (some? repository) "Repository is null")
  (assert (not (empty? data-col)) "Data collection is empty")
  (log/debug (format "Data collection [%s]: %s" (type data-col) (with-out-str (pp/pprint data-col))))
  (reduce + (pmap (fn [itm]
                    (log/infof "Load dataset: %s into context: %s" itm context-uri)
                    (cor/load-data repository (if (u/normalise-path-supportsp itm)
                                                (u/normalise-path itm) itm) :rdf-handler rdf-handler :context-uri context-uri)) data-col)))


(defn ^{:no-doc true} -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:8080/rdf4j-server"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"]
                               ["--file FILE" "-f" "Data file path" :assoc-fn #'u/multioption->seq]
                               ["--context IRI" "-c" "Context (graph name) of the dataset. Ignored if file format is context aware, e.g. TriG" :default nil]
                               ["--version" "-V" "Display program version" :defult false :flag true])]

    (cond
      (:h opts) (println banner)
      (:V opts) (println "Version: " rdf4j.version/version)
      :else (do-loading opts))))
