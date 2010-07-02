(ns cljr.swank
  (:use [swank.swank :only (start-repl)]))

(defn swank
  ([]
     (swank 4005))
  ([port]
     (cond
      (nil? port) (start-repl 4005)
      (integer? port) (start-repl port)
      (string? port) (start-repl (Integer/parseInt port 10))
      :else (println "Invalid port number: " port))))

