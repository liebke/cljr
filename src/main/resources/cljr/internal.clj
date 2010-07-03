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
			['org.clojure/clojure-contrib "1.2.0-SNAPSHOT" :exclusions '[org.clojure/clojure org.clojure/clojure-contrib]]
			['cljr "1.0.0-SNAPSHOT" :exclusions '[org.clojure/clojure org.clojure/clojure-contrib]]
			['leiningen "1.0.0" :exclusions '[org.clojure/clojure org.clojure/clojure-contrib]]
			['swingrepl "1.0.0-SNAPSHOT" :exclusions '[org.clojure/clojure org.clojure/clojure-contrib]]
			['jline "0.9.94"]
			['swank-clojure "1.2.1" :exclusions '[org.clojure/clojure org.clojure/clojure-contrib]]])


(defn excluded-dependencies []
  '[org.clojure/clojure org.clojure/clojure-contrib])


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
    [;; (str (get-cljr-home) (sep) cljr-jar)
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

	  "if [ \"$1\" == \"repl\" -o \"$1\" == \"swingrepl\" -o \"$1\" == \"swank\" -o \"$1\" == \"run\" ]; then\n\n"
	  "   if [ -n \"$CLOJURE_HOME\" ]; then\n\n"
	  "      for f in \"$CLOJURE_HOME\"/*.jar; do\n"
	  "         CLASSPATH=\"$CLASSPATH\":$f\n\n"
	  "      done\n\n"
	  "   fi\n\n"
	  "   for f in \"$CLJR_HOME\"/lib/*.jar; do\n"
	  "      CLASSPATH=\"$CLASSPATH\":$f\n"
	  "   done\n\n"
	  "else\n\n"
	  "   CLASSPATH=\"$CLASSPATH\":\"$CLJR_HOME\"/cljr.jar\n\n"
	  "fi\n\n"
	  
	  "if [ \"$1\" = \"repl\" ]; then\n"
	  "   java -cp \"$CLASSPATH\" -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" jline.ConsoleRunner clojure.main -e \"(require 'cljr.main) (cljr.main/initialize-classpath)\" -r\n\n" 
	  "else\n\n" 
	  "    java -cp \"$CLASSPATH\" -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" cljr.App $*\n\n" 
	  "fi\n\n")))




(defn cljr-bat-script
  ([]
     (str "@echo off\r\n"
	  "setLocal EnableDelayedExpansion\r\n"
	  "set CLJR_HOME=\"" (get-cljr-home) "\"\r\n"
	  "set USER_HOME=\"" (get-user-home) "\"\r\n"
	  "if not defined %CLOJURE_HOME% set CLOJURE_HOME=%CLJR_HOME%\\lib\r\n"

	  "set CLJR_ONLY=\"true\"\r\n"
	  "for /l %%X in (repl swingrepl swank run) do (\r\n"
	  "   if %1% eq %%X ( set CLJR_ONLY=\"false\" )\r\n"
	  ")\r\n"
	  
	  "if %CLJR_ONLY% eq \"false\" (\r\n"
	  "   if defined %CLOJURE_HOME% (\r\n"
	  "      set CLASSPATH=\"\r\n"
	  "         for /R %CLOJURE_HOME% %%a in (*.jar) do (\r\n"
	  "            set CLASSPATH=!CLASSPATH!;%%a\r\n"
	  "         )\r\n"
	  "         set CLASSPATH=!CLASSPATH!\"\r\n"
	  "   )\r\n"
	  "   set CLASSPATH=\"\r\n"
	  "      for /R ./lib %%a in (*.jar) do (\r\n"
	  "         set CLASSPATH=!CLASSPATH!;%%a\r\n"
	  "      )\r\n"
	  "      set CLASSPATH=!CLASSPATH!\"\r\n"
	  ") else (\r\n"
	  "   set CLASSPATH=\"%CLJR_HOME%\"\\cljr.jar\r\n"
	  ")\r\n"
  
	  "set CLASSPATH=%CLASSPATH%;src;test;.\r\n"
      
	  "@rem jline breaks inferior-lisp.\r\n"
	  "if /i %DISABLE_JLINE% neq \"true\" set JLINE=jline.ConsoleRunner\r\n"
	  
	  "if %1% eq \"repl\" (\r\n"
	  "   java -Xmx1G -Dcljr.home=%CLJR_HOME% -Dclojure.home=%CLOJURE_HOME% -Duser.home=%USER_HOME% -cp %CLASSPATH% %JLINE% clojure.main -e \"(require 'cljr.main) (cljr.main/initialize-classpath)\" -r\r\n"
	  ") else (\r\n"
	  "   java -Xmx1G -Dcljr.home=%CLJR_HOME% -Dclojure.home=%CLOJURE_HOME% -Duser.home=%USER_HOME% -cp %CLASSPATH% cljr.App %* \r\n"
	  ")\r\n")))

