(ns cljr.installer
  (:require cljr.main)
  (:gen-class))


(defn -main
  ([& args]
     (cljr.main/-main args)))
