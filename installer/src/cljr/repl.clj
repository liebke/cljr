(ns cljr.repl
  (:require cljr.main)
  (:gen-class))

(defn -main
  ([& args]
     (cljr.main/-main args)))
