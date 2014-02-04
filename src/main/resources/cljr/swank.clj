(ns cljr.swank
  (:use [swank.swank :only (start-repl)]))

(defn swank
  ([]
     (swank nil nil))
  ([port]
     (swank port nil))
  ([port host]
     (let [the-host (cond
                     (nil? host) "localhost"
                     (string? host) host
                     :else (do (println "Invalid hostname:" host)
                               nil))
           the-port (cond
                     (nil? port) 4005
                     (integer? port) port
                     (string? port) (try (Integer/parseInt port 10)
                                         (catch NumberFormatException ex
                                           nil)))]
       (when (and the-host the-port)
         (start-repl the-port :host the-host)))))

