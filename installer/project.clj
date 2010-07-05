(defproject cljr-installer "1.0.0-SNAPSHOT"
  :description "This is the cljr-installer, cljr is a Clojure REPL and package managment system."
  :dependencies [[cljr "1.0.0-SNAPSHOT"]]
;  :jar-name "cljr-only.jar" ; name of the jar produced by 'lein jar'
;  :uberjar-name "cljr-installer.jar" ; as above for uberjar
  :main cljr.installer)
