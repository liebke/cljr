(ns cljr.internal
  (:use [clojure.java.io :only (file copy)]
	[leiningen.core :only (read-project)])
  (:require [clojure.string :as s]))


(def CLJR-VERSION "1.0.0-SNAPSHOT")

(defn sep [] (java.io.File/separator))

(defn path-sep [] (java.io.File/pathSeparator))

(def cljr-jar "cljr.jar")

(def project-clj "project.clj")

(def classpath-uninitialized? (ref true))


(def base-dependencies [['org.clojure/clojure "1.2.0-master-SNAPSHOT"]
			['org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
			['cljr "1.0.0-SNAPSHOT"]
			['leiningen "1.0.0"]
			['swingrepl "1.0.0-SNAPSHOT"]
			['jline "0.9.94"]
			['swank-clojure "1.2.1"]])


(defn get-user-home []
  (System/getProperty "user.home"))


(defn get-cljr-home []
  (let [default-cljr-home (str (get-user-home) (sep) ".cljr")]
    (or (System/getProperty "cljr.home") default-cljr-home)))


(defn cljr-lib-dir []
  (str (get-cljr-home) (sep) "lib" (sep)))


(defn windows-os? []
  (.startsWith (System/getProperty "os.name") "Windows"))


(defn get-project []
  (let [project-file (file (str (get-cljr-home) (sep) project-clj))]
    (if (.exists project-file)
      (read-project (.getAbsolutePath project-file))
      (println (str (get-cljr-home) (sep) "project.clj does not exist, cljr must be initialized.")))))


(defn need-to-init?
  ([] (need-to-init? (get-cljr-home)))
  ([cljr-home]
     (not (and (.exists (file cljr-home project-clj))
	       (= "cljr-repo" (:name (get-project)))))))


(defn get-jars-classpath []
  (str (cljr-lib-dir) "*"))


(defn get-classpath-vector []
  (if (need-to-init?)
    [(str (get-cljr-home) (sep) cljr-jar)
     ;; (get-jars-classpath)
     "./src/" "./"]
    (vec (:classpath (get-project)))))


(defn get-dependencies []
  (:dependencies (get-project)))


(defn get-classpath-urls
  [classpath-vector]
  (map #(-> % file .toURI .toURL) classpath-vector))


(defn get-classpaths
  ([] (get-classpaths (get-classpath-vector)))
  ([classpath-vector]
     (s/join [(apply str (interpose (path-sep) classpath-vector))
	      (str (path-sep) (get-jars-classpath))])))


(defn project-clj-str
  ([] (project-clj-str base-dependencies
		       (get-classpath-vector)))
  ([dependency-vector classpath-vector]
     (pr-str `(leiningen.core/defproject cljr-repo "1.0.0-SNAPSHOT"
	       :description "cljr is a Clojure REPL and package management system."
	       :dependencies ~dependency-vector
	       :classpath ~classpath-vector))))


(defn cljr-sh-script
  ([]
     (str "#!/bin/sh\n\n"
     "USER_HOME=\"" (get-user-home) "\"\n"
     "CLJR_HOME=\"" (get-cljr-home) "\"\n" 
     "CLASSPATH=src:test:.\n\n"
     "if [ -n \"$CLOJURE_HOME\" ]; then\n"
     "   for f in \"$CLOJURE_HOME/*.jar\"; do\n"
     "      CLASSPATH= \"$CLASSPATH\":$f\n\n"
     "   done\n\n"
     "else\n\n"
    "   CLOJURE_HOME=\"$CLJR_HOME\"/lib\n"
    "fi\n\n"
    "for f in \"$CLJR_HOME\"/lib/*.jar; do\n"
    "   CLASSPATH=\"$CLASSPATH\":$f\n"
    "done\n\n"
    "if [ \"$1\" = \"repl\" ]; then\n"
   "   java -cp \"$CLASSPATH\" -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" jline.ConsoleRunner clojure.main  -e \"(require 'cljr.main) (cljr.main/initialize-classpath)\" -r\n" 
   "else\n\n" 
   "    java -cp \"$CLASSPATH\" -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" cljr.App $*\n" 
   "fi\n\n")))




(defn cljr-bat-script
  ([]
     (let [cljr-home (get-cljr-home)]
       (str "@echo off\r\n"
	    "java -cp \"" (get-cljr-home) (sep) cljr-jar "\" "
	    "-Duser.home=\"" (get-user-home) "\" -Dcljr.home=\"" (get-cljr-home)
	    "\" cljr.main %* \r\n"))))

