;; file name must follow namespace's name

(ns triple.loadder
  (:gen-class))


(defn -main [& args]
  ;; (printf "Current namespace: %s" (ns-name *ns*))
  (let [current-thread (Thread/currentThread)]
    (println "Run test")
    (println (format "Run test in thread: '%s':%d" (.getName current-thread)
                                                   (.getId current-thread))))
  )
