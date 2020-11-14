(ns rdf4j.file-reading-test
  (:use [clojure.java.io :as io]
        [clojure.test]
        [clojure.tools.logging :as log]
        [clojure.pprint :as pp]
        [clojure.java.io :as io]
        [clojure.tools.logging :as log]))



(deftest try-read-file
  (testing "Read the current directory"
    (let [path "resources/"
          file (io/file path)]
      (log/infof "File path %s" (.getAbsolutePath file))
      )
    )
  (testing "Using class loader"
    (let [path "resources/"
          resources (io/resource path)]
      (log/infof "Resources path %s" resources))
    ))
