(ns cljr.main
  (:use [cljr core clojars scripts]
        [leiningen.deps :only (deps)]
	[leiningen.clean :only (empty-directory)]
        [clojure.java.io :only (file copy)])
  (:require org.dipert.swingrepl.main
	    [clojure.string :as s]))




(defn cljr-help []
  (str \newline\newline
       "--------------------------------------------------------------------------------"
       \newline
       "cljr is a Clojure REPL and package management system." \newline
       \newline
       "Usage: cljr command [arguments]" \newline
       \newline
       "Available commands:" \newline
       \newline
       "*  repl: Starts a Clojure repl." \newline
       \newline
       "*  swingrepl: Starts a Clojure swingrepl." \newline
       \newline
       "*  swank [port]: Start a local swank server on port 4005 (or as specified)." \newline
       \newline
       "*  run filename: Runs the given Clojure file." \newline
       \newline
       "*  list: Prints a list of installed packages." \newline
       \newline
       "*  install package-name [package-version]: Installs the given package from " \newline
       "   clojars.org, defaulting to the inferred latest version." \newline
       \newline
       "*  search term: Prints a list of packages on clojars.org with names that contain " \newline
       "   the given search term." \newline
       \newline
       "*  describe package-name [package-version]: Prints the description of the given " \newline
       "   package as found in the description field of its pom file." \newline
       \newline
       "*  versions package-name: Prints a list of the versions of the given package " \newline
       "   available on clojars.org." \newline
       \newline
       "*  remove package-name: Removes given package from the cljr-repo package list, " \newline
       "   must be followed by 'cljr clean' and 'cljr reload' to actually remove packages " \newline
       "   from the repository." \newline
       \newline
       "*  clean: Removes all packages from $CLJR_HOME/lib directory." \newline
       \newline
       "*  reload: Reloads all packages in the cljr repository." \newline
       \newline
       "*  list-classpath: Prints classpath." \newline
       \newline
       "*  add-classpath dirs-or-jars: Adds directories or jar files to the classpath." \newline
       "   Directories should have a trailing / to distinguish them from jar files." \newline
       \newline
       "*  add-jars jar file(s): Copies jar files to the cljr repository." \newline
       "   Directories should have a trailing / to distinguish them from jar files." \newline
       \newline
       "*  remove-classpath dir-or-jar: Removes a directory or jar file from the classpath." \newline
       "   Remember to include trailing / for directories." \newline
       \newline
       "*  list-repos: Prints a list of repositories." \newline
       \newline
        "*  add-repo repo-name repo-url: Adds repository." \newline
	\newline
	"*  remove-repo repo-name: Removes the given repository." \newline
	\newline
	"*  list-jars: Prints a list of jars in the cljr repository." \newline
       \newline
       "*  help: Prints this message." \newline
       \newline
       \newline
       "Packages are installed in $CLJR_HOME/lib, and can be used by applications other " \newline
       "than cljr by including the jars in that directory on the classpath. For instance, " \newline
       "to start a command line REPL with jline, run the following command: "\newline
       \newline
       "   java -cp $CLJR_HOME/lib/'*' jline.ConsoleRunner clojure.main" \newline
       \newline))


(defn cljr-list-jars
  ([] (cljr-list-jars (get-cljr-home)))
  ([cljr-home]
     (let [cljr-repo (file cljr-home "lib")]
       (if-not (.isDirectory cljr-repo)
	 (println "The " (cljr-lib-dir) " repository does not exist, needs to be initialized.")
	 (let [files (seq (.listFiles cljr-repo))]
	   (println "\n\nList of jar files in the cljr repository:\n")
	   (println "--------------------------------------------------------------------------------")
	   (doseq [f files] (println (.getName f)))
	   (println "\n\n"))))))


(defn initialize-classpath
  ([] (let [cljr-home (get-cljr-home)
	    additional-classpaths (:classpath (get-project))]
	(initialize-classpath cljr-home)))
  ([cljr-home]
     (when @classpath-uninitialized?
       (let [cljr-repo (file cljr-home "lib")]
	 (if-not (.isDirectory cljr-repo)
	   (println "The " (cljr-lib-dir) " repository does not exist, needs to be initialized.")
	   (let [all-paths (full-classpath)
		 urls (map #(-> % .toURI .toURL) all-paths)
		 previous-classloader (.getContextClassLoader (Thread/currentThread))
		 current-classloader (java.net.URLClassLoader/newInstance (into-array urls))]
	     (.setContextClassLoader (Thread/currentThread) current-classloader)
	     (dosync (ref-set classpath-uninitialized? false))
	     (println "Clojure classpath initialized by cljr.")))))))


(defn cljr-clean []
  (empty-directory (file (:library-path (get-project))) true))


(defn cljr-reload []
  (cljr-clean)
  (deps (get-project)))


(defn cljr-remove [library-name]
  (let [project (get-project)
	dependencies (:dependencies project)
	updated-project (assoc project :dependencies
			       (into [] (filter #(not= (symbol library-name)
						       (first %))
						dependencies)))
	proj-str (project-clj-string updated-project
				     {:dependencies (:dependencies updated-project)})]
    (spit (str (get-cljr-home) (sep) project-clj) proj-str)
    (println "--------------------------------------------------------------------------------")
    (println (str "**" library-name " has been removed from the list of dependencies,\n"
		  "run 'cljr reload' to actually remove packages from the repo. **\n"))))


(defn cljr-self-install
  ([]
     (let [cljr-home (file (get-cljr-home))
	   cljr-lib (file cljr-home "lib")
	   cljr-src (file cljr-home "src")
	   cljr-bin (file cljr-home "bin")
	   current-jar  (file (first
	   		       (filter
	   			#(re-find (re-pattern (str "cljr-installer(\\.|-"
							   CLJR-VERSION
							   "-standalone\\.|-standalone\\.)(jar|zip)")) %)
	   			(s/split (System/getProperty "java.class.path")
	   				 (re-pattern (path-sep))))))]
       (if (need-to-init? cljr-home)
	 (do
	   (println "--------------------------------------------------------------------------------")
	   (println "Initializing cljr...")
	   (println "Creating cljr home, " (get-cljr-home) "...")
	   (doseq [d [cljr-home cljr-lib cljr-src cljr-bin]] (.mkdirs d))
	   (println (str "Copying " current-jar " to " cljr-home (sep) cljr-jar "..."))
	   (copy current-jar (file cljr-home cljr-jar))
	   (println (str "Creating " cljr-home (sep) project-clj " file..."))
	   (spit (file cljr-home project-clj) (base-project-clj-string))
	   (println "Creating script files...")
	   (doto (file cljr-bin "cljr")
	     (spit (cljr-sh-script))
	     (.setExecutable true))
	   (doto (file cljr-bin "cljr.bat")
	     (spit (cljr-bat-script))
	     (.setExecutable true))
	   (println "Loading core dependencies...")
	   (cljr-reload)
	   (println)
	   (println "** Installation complete. **")
	   (println)
	   (println "--------------------------------------------------------------------------------")
	   (println (str ))
	   (println (str "Cljr has been successfully installed."
			 " Add " cljr-home (sep) "bin to your PATH:\r\n\r\n"
			 "   $ export PATH=" cljr-home (sep) "bin:$PATH\r\n\r\n"
			 "Run 'cljr help' for a list of available commands.\r\n")))
	 (println (str "** " cljr-home " is already initialized. **"))))))


(defn cljr-add-jars [& jar-files]
  (println "Copying jar files to cljr repository...")
  (let [cljr-lib (file (cljr-lib-dir))
	files (map file jar-files)]
    (doseq [f files]
      (copy (file f) (file cljr-lib (.getName f))))
    (println "jar files copied.")))


(defn cljr-classpath []
  (println "\n\nCurrent Classpath:")
  (println "--------------------------------------------------------------------------------")
  (doseq [p (:classpath (get-project))]
    (println (str "  " p)))
  (println (str "  " (get-jars-classpath) \newline \newline)))


(defn cljr-repl []
  (org.dipert.swingrepl.main/make-repl-jframe 
   {:on-close javax.swing.JFrame/EXIT_ON_CLOSE}))


(defn cljr-run [filename]
  (apply clojure.main/main filename))


(defn cljr-list []
  (let [dependencies (:dependencies (get-project))]
    (println "\n\nCurrently installed libraries:")
    (println "--------------------------------------------------------------------------------")
    (doseq [dep dependencies]
      (println "  " (first dep) "  " (second dep)))
    (println "\n\n")))


(defn cljr-list-repos []
  (let [repos (merge leiningen.pom/default-repos
		     (:repositories (get-project)))]
    (println "\n\nAvailable repositories:")
    (println "--------------------------------------------------------------------------------")
    (doseq [repo repos]
      (println "  " (first repo) "  " (second repo)))
    (println "\n\n")))


(defn cljr-add-classpath
  ([& classpath]
     (let [classpath-vector (flatten (conj (get-classpath-vector) classpath))]
       ;; generate a new project.clj
       (spit (file (str (get-cljr-home) (sep) project-clj))
	     (project-clj-string (get-project)
				 {:classpath classpath-vector})))))


(defn cljr-remove-classpath
  ([classpath]
     (let [classpath-vector (into [] (filter #(not= classpath %) (get-classpath-vector)))]
       ;; generate a new project.clj
       (spit (file (str (get-cljr-home) (sep) project-clj))
	     (project-clj-string (get-project)
				 {:classpath classpath-vector})))))
  
(defn cljr-add-repo
  ([repo-name repo-url]
     (let [repo-map (assoc (get-repositories) repo-name repo-url)]
       ;; generate a new project.clj
       (println "--------------------------------------------------------------------------------")
       (println "Adding " repo-name " (" repo-url ") to the list of available repositories...")
       (spit (file (str (get-cljr-home) (sep) project-clj))
	     (project-clj-string (get-project)
				 {:repositories repo-map}))
       (println repo-name " repository added.\n\n"))))


(defn cljr-remove-repo
  ([repo-name]
     (let [repo-map (dissoc (get-repositories) repo-name)]
       ;; generate a new project.clj
       (println "--------------------------------------------------------------------------------")
       (println "Removing " repo-name " from the list of available repositories...")
       (spit (file (str (get-cljr-home) (sep) project-clj))
	     (project-clj-string (get-project)
				 {:repositories repo-map}))
       (println repo-name " repository removed.\n\n"))))


(defn cljr
  "Provides access to the cljr package management system. It uses the same arguments
  as the command line version, using keywords for commands and strings for arguments."
  ([]
     (if (need-to-init?)
       (cljr :self-install)
       (cljr :repl)))
  ([& args]
     (initialize-classpath)
     (let [cmd (keyword (first args))
	   opts (rest args)]
       (condp = cmd
	   :self-install (cljr-self-install)
	   :reload (cljr-reload)
	   :list (cljr-list)
	   :list-repos (cljr-list-repos)
	   :install (apply clojars-install opts)
	   :remove (apply cljr-remove opts)
	   :clean (cljr-clean)
	   :search (apply clojars-search opts)
	   :versions (apply clojars-versions opts)
	   :describe (apply clojars-describe opts)
	   :repl (cljr-repl)
	   :swingrepl (cljr-repl)
	   :run (cljr-run opts)
	   :list-classpath (cljr-classpath)
	   :add-classpath (apply cljr-add-classpath opts)
	   :add-jars (apply cljr-add-jars opts)
	   :remove-classpath (apply cljr-remove-classpath opts)
	   :add-repo (apply cljr-add-repo opts)
	   :remove-repo (apply cljr-remove-repo opts)
	   :list-jars (cljr-list-jars)
	   :help (println (cljr-help))
	   (apply run-cljr-task (name cmd) opts)))))


(defn -main
  ([args]
     (apply cljr args)))
