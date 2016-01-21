;; file name must follow namespace's name

(ns triple.loadder
  (:gen-class)
  (:require [clojure.tools.cli :refer [cli]]))


(defn -main [& args]
  (let [[opts args banner] (cli args
                               ["--help" "-h" "Print this screen" :default false :flag true]
                               ["--server URL" "-s" "Sesame SPARQL endpoint URL" :default "http://localhost:9090/openrdf-sesame"]
                               ["--repositiry NAME" "-r" "Repository id" :default "test"])]
    ;; print help message
  (when (:h opts)
    (println banner))

  
  ))


