(ns clj.main
  (:use [leiningen.core :only (read-project)]
        [leiningen.deps :only (deps)]
	[leiningen.clean :only (empty-directory)]
	[clojure-http.client :only (request)]
        [clojure.java.io :only (file copy)])
  (:require org.dipert.swingrepl.main
	    [clojure.string :as s])
  (:gen-class))

(def CLJ-VERSION "1.0.0-SNAPSHOT")


(defn help-text []
  (str \newline\newline
       "clj is a Clojure REPL and package management system." \newline\newline
       "Usage: clj command [arguments]" \newline\newline
       "Available commands:" \newline
       "*  repl: Starts a Clojure swingrepl." \newline
       "*  list: Prints a list of installed packages." \newline
       "*  search term: Prints a list of packages on clojars.org with names that contain the given search term." \newline
       "*  install package-name [package-version]: Installs the given package from clojars.org, defaulting to the inferred latest version." \newline
       "*  describe package-name [package-version]: Prints the description of the given package as found in the description field of its pom file." \newline
       "*  versions package-name: Prints a list of the versions of the given package available on clojars.org" \newline
       "*  remove package-name: Removes given package from the clj-repo dependency list, then reinstalls all packages." \newline
       \newline))

(defn get-clj-home []
  (let [user-home (System/getProperty "user.home")
	clj-home (file (str user-home "/.clj"))]
    clj-home))


(defn clj-sh-script []
  (str "#!/bin/sh" \newline
       "CLJ_HOME=$HOME/.clj" \newline
       "CLASSPATH=$CLJ_HOME/src:$CLJ_HOME/script:$CLJ_HOME/clj.jar:$CLJ_HOME/lib/'*':." \newline
       "java -cp \"$CLASSPATH\" -Dclj.home=\"$CLJ_HOME\" clj.main $*" \newline))


(defn clj-bat-script []
  (let [clj-home (get-clj-home)]
    (str "@echo off" \newline
	 "set CLJ_HOME=\"" clj-home "\"" \newline
	 "setLocal EnableDelayedExpansion" \newline
	 "set CLASSPATH=\"" \newline
	 "for /R %CLJ_HOME%/lib %%a in (*.jar) do (" \newline
	 "  set CLASSPATH=!CLASSPATH!;%%a" \newline
	 ")" \newline
	 "set CLASSPATH=!CLASSPATH!\"" \newline
	 "set CLASSPATH=%CLASSPATH%;src;." \newline
	 "java -cp \"$CLASSPATH\" -Dclj.home=\"%CLJ_HOME%\" clj.main $*" \newline)))


(defn clj-project-clj []
  (str "(leiningen.core/defproject clj-repo \"1.0.0-SNAPSHOT\" \n"
       "  :description \"clj is a Clojure REPL and package managment system.\"\n"
       "  :dependencies [[org.clojure/clojure \"1.2.0-master-SNAPSHOT\"]\n"
       "                 [org.clojure/clojure-contrib \"1.2.0-SNAPSHOT\"]\n"
       "                 [leiningen \"1.0.0\"]\n"
       "                 [swingrepl \"1.0.0-SNAPSHOT\"]\n"
       "                 [clojure-http-client \"1.1.0-SNAPSHOT\"]])\n"))


(defn get-project []
  (let [project-file (str (get-clj-home) "/project.clj")]
    (read-project project-file)))


(defn need-to-init?
  ([] (need-to-init? (get-clj-home)))
  ([clj-home]
     (not (and (.exists (file (str clj-home "/project.clj")))
	       (= "clj-repo" (:name (get-project)))))))


(defn clj-deps
  ([] (deps (get-project))))


(defn clj-self-install
  ([] (clj-self-install (get-clj-home)))
  ([clj-home]
     (let [clj-lib (file (str clj-home "/lib"))
	   clj-src (file (str clj-home "/src"))
	   clj-bin (file (str clj-home "/bin"))
	   current-jar  (file (first
			       (filter
				#(.endsWith % (str "clj-" CLJ-VERSION "-standalone.jar"))
				(s/split (System/getProperty "java.class.path")
					 #":"))))]
       (if (need-to-init? clj-home)
	 (do
	   (println "Initializing clj...")
	   (println "Creating ~/.clj directory structure...")
	   (doseq [d [clj-home clj-lib clj-src clj-bin]] (.mkdirs d))
	   (println (str "Copying " current-jar " to " clj-home "/clj.jar..."))
	   (copy current-jar (file (str clj-home "/clj.jar")))
	   (println "Creating ~/.clj/project.clj file...")
	   (spit (file clj-home "project.clj") (clj-project-clj))
	   (println "Creating script files...")
	   (doto (file clj-bin "clj")
	     (spit (clj-sh-script))
	     (.setExecutable true))
	   (doto (file clj-bin "clj.bat")
	     (spit (clj-bat-script))
	     (.setExecutable true))
	   (println "Loading core dependencies...")
	   (clj-deps)
	   (println "Installation complete.")
	   (println (str "Add ~/.clj/bin to your PATH:" \newline
			 "export PATH=~/.clj/bin:$PATH")))
	 (println clj-home " is already initialized.")))))


(defn get-latest-version [library-name]
  (let [response (request "http://clojars.org/repo/all-jars.clj")]
    (second (last (filter #(= (first %) (symbol library-name))
			  (for [line (:body-seq response)]
			    (read-string line)))))))


(defn clj-install
  ([library-name]
     (let [latest-version (get-latest-version library-name)]
       (clj-install library-name (get-latest-version library-name))))
  ([library-name library-version]
     (let [project (get-project)
	   dependencies (:dependencies project)
	   updated-project (assoc project :dependencies
				  (conj dependencies
					[(symbol library-name) library-version]))
	   proj-str (str "(leiningen.core/defproject "
			 (:name updated-project) " \""
			 (:version updated-project) "\"" \newline
			 ":description \"" (:description updated-project) "\"" \newline
			 ":dependencies " (:dependencies updated-project) ")")]
       (println "Installing version " library-version " of " library-name "...")
       (spit (str (get-clj-home) "/project.clj") proj-str)
       (clj-deps))))


(defn clj-clean []
  (empty-directory (file (:library-path (get-project))) true))


(defn clj-remove [library-name]
  (let [project (get-project)
	dependencies (:dependencies project)
	updated-project (assoc project :dependencies
			       (into [] (filter #(not= (symbol library-name) (first %))
						dependencies)))
	proj-str (str "(leiningen.core/defproject "
		      (:name updated-project) " \""
		      (:version updated-project) "\"" \newline
		      ":description \"" (:description updated-project) "\"" \newline
		      ":dependencies " (:dependencies updated-project) ")")]
    (spit (str (get-clj-home) "/project.clj") proj-str)
    (clj-clean)
    (clj-deps)))


(defn clj-repl []
  (org.dipert.swingrepl.main/make-repl-jframe 
   {:on-close javax.swing.JFrame/EXIT_ON_CLOSE}))


(defn clj-list []
  (let [dependencies (:dependencies (get-project))]
    (println "\n\nCurrently installed libraries:")
    (println "------------------------------")
    (doseq [dep dependencies]
      (println "  " (first dep) "  " (second dep)))
    (println "\n\n")))


(defn clj-list-repos []
  (let [repos (or (:repositories (get-project))
		  leiningen.pom/default-repos)]
    (println "\n\nAvailable repositories:")
    (println "------------------------------")
    (doseq [repo repos]
      (println "  " (first repo) "  " (second repo)))
    (println "\n\n")))


(defn clj-search [term]
  (let [response (request "http://clojars.org/repo/all-jars.clj")]
    (println "\n\nLibraries on Clojars.org that contain the term: " term)
    (println "---------------------------------------------------------------")
    (doseq [entry (for [line (:body-seq response) :when (.contains line term)]
		    (read-string line))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))


(defn clj-versions [library-name]
  (let [response (request "http://clojars.org/repo/all-jars.clj")]
    (println "\n\nAvailable versions for library: " library-name)
    (println "---------------------------------------------------------------")
    (doseq [entry (filter #(= (first %) (symbol library-name))
			  (for [line (:body-seq response)]
			    (read-string line)))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))


(defn get-description [id-str]
  (let [response (request (str "http://clojars.org/repo/" id-str))]
    (-> (apply str
	       (for [line (:body-seq response)
		     :when (.contains line "<description>")]
		 line))
	s/trim
	(s/replace "<description>" "")
	(s/replace "</description>" ""))))


(defn clj-describe
  ([library-name]
     (clj-describe library-name (get-latest-version library-name)))
  ([library-name version]
     (let [response (request "http://clojars.org/repo/all-poms.txt")
	   id-str (if (.contains library-name "/")
		    (str "./" library-name "/" version)
		    (str "./" library-name "/" library-name "/" version))]
       (println (str "\n\nDescription for library: " library-name "  " version))
       (println "---------------------------------------------------------------")
       (println (get-description
		 (last (for [line (:body-seq response)
			     :when (.startsWith line id-str)]
			 line))))
       (println "\n\n"))))


(defn clj
  "Provides access to the clj package management system. It uses the same arguments
  as the command line version, using keywords for commands and strings for arguments."
  [command & args]
  (let [cmd (keyword command)]
    (condp = cmd
	:self-install (clj-self-install)
	:list (clj-list)
	:list-repos (clj-list-repos)
	:install (apply clj-install args)
	:remove (apply clj-remove args)
	:clean (clj-clean)
	:search (apply clj-search args)
	:versions (apply clj-versions args)
	:describe (apply clj-describe args)
	:repl (clj-repl)
	:help (println (help-text))
	(println "unrecognized command to clj."))))


(defn -main
  ([] (if (need-to-init?)
	(clj :self-install)
	(clj :repl)))
  ([& args] (apply clj args)))

