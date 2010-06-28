(ns clj.internal
  (:use [clojure.java.io :only (file copy)]
	[leiningen.core :only (read-project)])
  (:require [clojure.string :as s]))


(def CLJ-VERSION "1.0.0-SNAPSHOT")

(defn sep [] (java.io.File/separator))

(defn path-sep [] (java.io.File/pathSeparator))

(def clj-jar "clj.jar")

(def project-clj "project.clj")

(def classpath-uninitialized? (ref true))


(def base-dependencies [['org.clojure/clojure "1.2.0-master-SNAPSHOT"]
			['org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
			['leiningen "1.0.0"]
			['swingrepl "1.0.0-SNAPSHOT"]
			['jline "0.9.94"]
			['swank-clojure "1.2.1"]])


(defn get-user-home []
  (System/getProperty "user.home"))


(defn get-clj-home []
  (let [default-clj-home (str (get-user-home) (sep) ".clj")]
    (or (System/getProperty "clj.home") default-clj-home)))


(defn clj-lib-dir []
  (str (get-clj-home) (sep) "lib" (sep)))


(defn windows-os? []
  (.startsWith (System/getProperty "os.name") "Windows"))


(defn get-project []
  (let [project-file (file (str (get-clj-home) (sep) project-clj))]
    (if (.exists project-file)
      (read-project (.getAbsolutePath project-file))
      (println (get-clj-home) (sep) "project.clj does not exist, clj must be initialized."))))


(defn need-to-init?
  ([] (need-to-init? (get-clj-home)))
  ([clj-home]
     (not (and (.exists (file clj-home project-clj))
	       (= "clj-repo" (:name (get-project)))))))


(defn get-jars-classpath []
  (str (clj-lib-dir) "*"))


(defn get-classpath-vector []
  (if (need-to-init?)
    [(str (get-clj-home) (sep) clj-jar)
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
     (pr-str `(leiningen.core/defproject clj-repo "1.0.0-SNAPSHOT"
	       :description "clj is a Clojure REPL and package management system."
	       :dependencies ~dependency-vector
	       :classpath ~classpath-vector
	       :main clj.main))))


(defn clj-sh-script
  ([]
     (str "#!/bin/sh\n"
	  "CLJ_HOME=\"" (get-clj-home) "\" \n" 
	  "if [ \"$1\" = \"repl\" ]; then \n"
	  "   java -cp \"" (get-clj-home) (sep) clj-jar "\" "
	  "-Duser.home=\"" (get-user-home) "\" "
	  "-Dclj.home=\"$CLJ_HOME\" jline.ConsoleRunner clojure.main "
	  " -e \"(require 'clj.main) (clj.main/initialize-classpath)\" -r "
	  "\n" 
	  "else \n"
	  "   java -cp \"" (get-clj-home) (sep) clj-jar "\" "
	  "-Duser.home=\"" (get-user-home) "\" -Dclj.home=\"$CLJ_HOME\" clj.main $* \n"
	  "fi \n")))


(defn clj-bat-script
  ([]
     (let [clj-home (get-clj-home)]
       (str "@echo off\r\n"
	    "java -cp \"" (get-clj-home) (sep) clj-jar "\" "
	    "-Duser.home=\"" (get-user-home) "\" -Dclj.home=\"" (get-clj-home)
	    "\" clj.main %* \r\n"))))

