(ns cljr.core
  (:use [clojure.java.io :only (file copy)]
	[leiningen.core :only (read-project)])
  (:require [clojure.string :as s]))


(def CLJR-VERSION "1.0.0-SNAPSHOT")

(defn sep [] (java.io.File/separator))

(defn path-sep [] (java.io.File/pathSeparator))

(def cljr-jar "cljr.jar")

(def project-clj "project.clj")

(def classpath-uninitialized? (ref true))


(def base-dependencies [['org.clojure/clojure "1.2.0-beta1"]
			['org.clojure/clojure-contrib "1.2.0-beta1"]
			['cljr  CLJR-VERSION]
			['leiningen "1.1.0"]
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
     (not (.exists (file cljr-home project-clj)))))


(defn get-jars-classpath []
  (str (cljr-lib-dir) "*"))


(defn get-classpath-vector []
  (if (need-to-init?)
    ["./src/" "./"]
    (vec (:classpath (get-project)))))


(defn get-dependencies []
  (:dependencies (get-project)))


(defn get-classpath-urls
  [classpath-vector]
  (map #(-> % file .toURI .toURL) classpath-vector))


(defn get-repositories []
  (let [repos (:repositories (get-project))]
    (or repos {})))


(defn get-classpaths
  ([] (get-classpaths (get-classpath-vector)))
  ([classpath-vector]
     (s/join [(apply str (interpose (path-sep) classpath-vector))
	      (str (path-sep) (get-jars-classpath))])))


(defn base-project-clj-string []
  (pr-str `(leiningen.core/defproject cljr-repo "1.0.0-SNAPSHOT"
	     :description "cljr is a Clojure REPL and package management system."
	     :dependencies ~base-dependencies
	     :classpath ~(get-classpath-vector))))


(defn project-clj-string
  ([] (get-project) {})
  ([project-clj entry-map]
     (let [deps (or (:dependencies entry-map) (:dependencies project-clj))
	   classpath (or (:classpath entry-map) (:classpath project-clj))
	   repos (or (:repositories entry-map) (:repositories project-clj))]
       (pr-str `(leiningen.core/defproject cljr-repo "1.0.0-SNAPSHOT"
		  :description "cljr is a Clojure REPL and package management system."
		  :dependencies ~deps
		  :classpath ~classpath
		  :repositories ~repos)))))


(defn abort [msg]
  (println msg)
  (System/exit 1))


(defn task-not-found [& _]
  (abort "That's not a task. Use \"cljr help\" to list all tasks."))


(defn resolve-task [task]
  (let [task-ns (symbol (str "cljr." task))
        task (symbol task)]
    (try
     (when-not (find-ns task-ns)
       (require task-ns))
     (or (ns-resolve task-ns task)
         #'task-not-found)
     (catch java.io.FileNotFoundException e
       #'task-not-found))))


(defn run-cljr-task
  [& [task-name & args]]
  (let [task (resolve-task task-name)
               value (apply task args)]
           (when (integer? value)
             (System/exit value))))


(defn get-clojure-home-jars []
  (let [clojure-home (System/getProperty "clojure.home")]
    (when clojure-home
      (filter #(.endsWith (.getName %) ".jar")
	      (seq (.listFiles (file clojure-home)))))))


(defn include-cljr-repo-jars? []
  (not= "false" (System/getProperty "include.cljr.repo.jars")))


(defn full-classpath []
  (let [cljr-repo (file (get-cljr-home) "lib")
	additional-paths (get-classpath-urls (get-classpath-vector))
	clojure-home-jars (when (include-cljr-repo-jars?)
			    (get-clojure-home-jars))
	jar-files (when (include-cljr-repo-jars?)
		    (seq (.listFiles cljr-repo)))]
    (filter identity (flatten (conj clojure-home-jars jar-files additional-paths)))))




