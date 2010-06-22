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

(defn sep [] (java.io.File/separator))

(defn path-sep [] (java.io.File/pathSeparator))

(def clj-jar "clj.jar")

(def project-clj "project.clj")


(defn help-text []
  (str \newline\newline
       "--------------------------------------------------------------------------------"
       \newline
       "clj is a Clojure REPL and package management system." \newline
       \newline
       "Usage: clj command [arguments]" \newline
       \newline
       "Available commands:" \newline
       \newline
       "*  repl: Starts a Clojure repl." \newline
       \newline
       "*  swingrepl: Starts a Clojure swingrepl." \newline
       \newline
       "*  run filename: Runs the given Clojure file." \newline
       \newline
       "*  list: Prints a list of installed packages." \newline
       \newline
       "*  search term: Prints a list of packages on clojars.org with names that contain " \newline
       "   the given search term." \newline
       \newline
       "*  install package-name [package-version]: Installs the given package from " \newline
       "   clojars.org, defaulting to the inferred latest version." \newline
       \newline
       "*  describe package-name [package-version]: Prints the description of the given " \newline
       "   package as found in the description field of its pom file." \newline
       \newline
       "*  versions package-name: Prints a list of the versions of the given package " \newline
       "   available on clojars.org" \newline
       \newline
       "*  remove package-name: Removes given package from the clj-repo package list, " \newline
       "   must be followed by 'clj clean' and 'clj reload' to actually remove packages " \newline
       "   from the repository." \newline
       \newline
       "*  clean: Removes all packages from $CLJ_HOME/lib directory." \newline
       \newline
       "*  reload: Reloads all packages listed by 'clj list'." \newline
       \newline
       "*  add-classpath classpath: Adds classpath to $CLJ_HOME/bin/clj(.bat) files" \newline
       \newline
       "*  remove-classpath classpath: Removes classpath from $CLJ_HOME/bin/clj(.bat) files" \newline
       \newline
       \newline
       "Packages are installed in $CLJ_HOME/lib, and can be used by applications other " \newline
       "than clj by including the jars in that directory on the classpath. For instance, " \newline
       "to start a command line REPL with jline, run the following command: "\newline
       \newline
       "   java -cp $CLJ_HOME/lib/'*' jline.ConsoleRunner clojure.main" \newline
       \newline))


(defn get-user-home []
  (System/getProperty "user.home"))


(defn get-clj-home []
  (let [default-clj-home (str (get-user-home) (sep) ".clj")]
    (or (System/getProperty "clj.home") default-clj-home)))


(defn windows-os? []
  (.startsWith (System/getProperty "os.name") "Windows"))


(defn get-project []
  (let [project-file (str (get-clj-home) (sep) project-clj)]
    (read-project project-file)))


(defn need-to-init?
  ([] (need-to-init? (get-clj-home)))
  ([clj-home]
     (not (and (.exists (file clj-home project-clj))
	       (= "clj-repo" (:name (get-project)))))))


(defn get-jars-classpath []
  (str (get-clj-home) (sep) "lib" (sep) "*"))


(defn get-classpath-vector []
  (if (need-to-init?)
    [(str (get-clj-home) (sep) clj-jar)
     ;; (get-jars-classpath)
     "src" "."]
    (:classpath (get-project))))


(defn get-classpaths
  ([] (get-classpaths (get-classpath-vector)))
  ([classpath-vector]
     (s/join [(apply str (interpose (path-sep) classpath-vector))
	      (str (path-sep) (get-jars-classpath))])))


(defn clj-project-clj
  ([] (clj-project-clj (get-classpath-vector)))
  ([classpath-vector]
    (str "(leiningen.core/defproject clj-repo \"1.0.0-SNAPSHOT\" \n"
	 "  :description \"clj is a Clojure REPL and package managment system.\"\n"
	 "  :dependencies [[org.clojure/clojure \"1.2.0-master-SNAPSHOT\"]\n"
	 "                 [org.clojure/clojure-contrib \"1.2.0-SNAPSHOT\"]\n"
	 "                 [leiningen \"1.0.0\"]\n"
	 "                 [swingrepl \"1.0.0-SNAPSHOT\"]\n"
	 "                 [jline \"0.9.94\"]\n"
	 "                 [clojure-http-client \"1.1.0-SNAPSHOT\"]]\n"
	 "  :classpath " classpath-vector ")\n")))


(defn clj-sh-script
  ([] (clj-sh-script (get-classpaths)))
  ([classpaths]
     (str "#!/bin/sh\n"
	  "CLJ_HOME=\"" (get-clj-home) "\" \n" 
	  "CLASSPATH=\"" classpaths "\" \n"
	  "if [ \"$1\" = \"repl\" ]; then \n"
	  "   java -cp $CLASSPATH -Duser.home=\"" (get-user-home) "\" -Dclj.home=$CLJ_HOME jline.ConsoleRunner clojure.main \n" 
	  "else \n"
	  "   java -cp $CLASSPATH -Duser.home=\"" (get-user-home) "\" -Dclj.home=$CLJ_HOME clj.main $* \n"
	  "fi \n")))


(defn clj-bat-script
  ([] (clj-bat-script (get-classpaths)))
  ([classpaths]
     (let [clj-home (get-clj-home)]
       (str "@echo off\r\n"
	    "set CLASSPATH=\"" classpaths "\"\r\n"
	    "java -Duser.home=" (get-user-home) " -Dclj.home=" (get-clj-home)
	    " -cp %CLASSPATH% " 
	    "clj.main %* \r\n"))))



(defn clj-reload []
  (deps (get-project)))


(defn clj-self-install
  ([]
     (let [clj-home (file (get-clj-home))
	   clj-lib (file clj-home "lib")
	   clj-src (file clj-home "src")
	   clj-bin (file clj-home "bin")
	   current-jar  (file (first
			       (filter
				#(or (.endsWith % (str "clj-standalone.jar"))
				     (.endsWith % (str "clj-" CLJ-VERSION "-standalone.jar")))
				(s/split (System/getProperty "java.class.path")
					 (re-pattern (path-sep))))))]
       (if (need-to-init? clj-home)
	 (do
	   (println "--------------------------------------------------------------------------------")
	   (println "Initializing clj...")
	   (println "Creating clj home, " (get-clj-home) "...")
	   (doseq [d [clj-home clj-lib clj-src clj-bin]] (.mkdirs d))
	   (println (str "Copying " current-jar " to " clj-home (sep) clj-jar "..."))
	   (copy current-jar (file clj-home clj-jar))
	   (println (str "Creating " clj-home (sep) project-clj " file..."))
	   (spit (file clj-home project-clj) (clj-project-clj))
	   (println "Creating script files...")
	   (doto (file clj-bin "clj")
	     (spit (clj-sh-script))
	     (.setExecutable true))
	   (doto (file clj-bin "clj.bat")
	     (spit (clj-bat-script))
	     (.setExecutable true))
	   (println "Loading core dependencies...")
	   (clj-reload)
	   (println)
	   (println "** Installation complete. **")
	   (println)
	   (println "--------------------------------------------------------------------------------")
	   (println (str "Add " clj-home (sep) "bin to your PATH:" \newline
			 "   export PATH=" clj-home (sep) "bin:$PATH" \newline\newline)))
	 (println (str "** " clj-home " is already initialized. **"))))))


(defn get-latest-version [library-name]
  (let [response (request "http://clojars.org/repo/all-jars.clj")
	lib-name (symbol library-name)]
    (second (last (filter #(= (first %) lib-name)
			  (for [line (:body-seq response)]
			    (read-string line)))))))


(defn clj-install
  ([library-name]
    (clj-install library-name (get-latest-version library-name)))
  ([library-name library-version]
    (let [project (get-project)
	  dependencies (:dependencies project)
	  updated-project (assoc project :dependencies
				 (conj dependencies
				       [(symbol library-name)
					library-version]))
	  proj-str (str "(leiningen.core/defproject "
			(:name updated-project) " \""
			(:version updated-project) "\"" \newline
			":description \"" (:description updated-project) "\"" \newline
			":dependencies " (:dependencies updated-project) ")")]
      (println "Installing version " library-version " of " library-name "...")
      (spit (str (get-clj-home) (sep) project-clj) proj-str)
      (clj-reload))))


(defn clj-clean []
  (empty-directory (file (:library-path (get-project))) true))


(defn clj-remove [library-name]
  (let [project (get-project)
	dependencies (:dependencies project)
	updated-project (assoc project :dependencies
			       (into [] (filter #(not= (symbol library-name)
						       (first %))
						dependencies)))
	proj-str (str "(leiningen.core/defproject "
		      (:name updated-project) " \""
		      (:version updated-project) "\"" \newline
		      ":description \"" (:description updated-project) "\"" \newline
		      ":dependencies " (:dependencies updated-project) ")")]
    (spit (str (get-clj-home) (sep) project-clj) proj-str)
    ;; (clj-clean)
    ;; (clj-reload)
    (println "Remember to run 'clj clean' and 'clj reload' to actually remove packages from the repo.")))


(defn clj-repl []
  (org.dipert.swingrepl.main/make-repl-jframe 
   {:on-close javax.swing.JFrame/EXIT_ON_CLOSE}))


(defn clj-run [filename]
  (apply clojure.main/main filename))


(defn clj-list []
  (let [dependencies (:dependencies (get-project))]
    (println "\n\nCurrently installed libraries:")
    (println "--------------------------------------------------------------------------------")
    (doseq [dep dependencies]
      (println "  " (first dep) "  " (second dep)))
    (println "\n\n")))


(defn clj-list-repos []
  (let [repos (or (:repositories (get-project))
		  leiningen.pom/default-repos)]
    (println "\n\nAvailable repositories:")
    (println "--------------------------------------------------------------------------------")
    (doseq [repo repos]
      (println "  " (first repo) "  " (second repo)))
    (println "\n\n")))


(defn clj-search [term]
  (let [response (request "http://clojars.org/repo/all-jars.clj")]
    (println "\n\nLibraries on Clojars.org that contain the term: " term)
    (println "--------------------------------------------------------------------------------")
    (doseq [entry (for [line (:body-seq response) :when (.contains line term)]
		    (read-string line))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))


(defn clj-versions [library-name]
  (let [response (request "http://clojars.org/repo/all-jars.clj")]
    (println "\n\nAvailable versions for library: " library-name)
    (println "--------------------------------------------------------------------------------")
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
       (println "--------------------------------------------------------------------------------")
       (println (get-description
		 (last (for [line (:body-seq response)
			     :when (.startsWith line id-str)]
			 line))))
       (println "\n\n"))))


(defn clj-add-classpath
  ([classpath]
     (let [classpath-vector (conj (get-classpath-vector) classpath)
	   classpaths (get-classpaths classpath-vector)]
       ;; generate a new project.clj
       (spit (file (str (get-clj-home) (sep) project-clj))
	     (clj-project-clj classpath-vector))
       ;; generate a new clj sh script
       (spit (file (str (get-clj-home) (sep) "bin" (sep) "clj"))
	     (clj-sh-script classpaths))
       ;; generate a new clj bat script
       (spit (file (str (get-clj-home) (sep) "bin" (sep) "clj.bat"))
	     (clj-bat-script classpaths)))))


(defn clj-remove-classpath
  ([classpath]
     (let [classpath-vector (into [] (filter #(not= classpath %) (get-classpath-vector)))
	   classpaths (get-classpaths classpath-vector)]
       ;; generate a new project.clj
       (spit (file (str (get-clj-home) (sep) project-clj))
	     (clj-project-clj classpath-vector))
       ;; generate a new clj sh script
       (spit (file (str (get-clj-home) (sep) "bin" (sep) "clj"))
	     (clj-sh-script classpaths))
       ;; generate a new clj bat script
       (spit (file (str (get-clj-home) (sep) "bin" (sep) "clj.bat"))
	     (clj-bat-script classpaths)))))
  

(defn clj
  "Provides access to the clj package management system. It uses the same arguments
  as the command line version, using keywords for commands and strings for arguments."
  ([]
     (if (need-to-init?)
       (clj :self-install)
       (clj :repl)))
  ([command & args]
     (let [cmd (keyword command)]
       (condp = cmd
	   :self-install (clj-self-install)
	   :reload (clj-reload)
	   :list (clj-list)
	   :list-repos (clj-list-repos)
	   :install (apply clj-install args)
	   :remove (apply clj-remove args)
	   :clean (clj-clean)
	   :search (apply clj-search args)
	   :versions (apply clj-versions args)
	   :describe (apply clj-describe args)
	   :repl (clj-repl)
	   :swingrepl (clj-repl)
	   :run (clj-run args)
	   :add-classpath (apply clj-add-classpath args)
	   :remove-classpath (apply clj-remove-classpath args)
	   :help (println (help-text))
	   (println "unrecognized command to clj.")))))


(defn -main
  ([& args] (apply clj args)))

