(ns triple.loader-test
  (:use [triple.loader]
        [clojure.test]))

(deftest connect-triple
  (let [server-url "http://localhost:8080/openrdf-sesame"
        repository-id "raw"
        repository (init-connection server-url repository-id)
        ]
    (some? repository)
    (with-open [c (.getConnection repository)]
      (println "Repository connection class: " (class c))
      (println "Get datadir: " (.getDataDir repository))
      )
    )
  )


