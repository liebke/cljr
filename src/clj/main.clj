(ns clj.main
  (:use [clj internal clojars]
        [leiningen.deps :only (deps)]
	[leiningen.clean :only (empty-directory)]
        [clojure.java.io :only (file copy)])
  (:require org.dipert.swingrepl.main
	    [clojure.string :as s])
  (:gen-class))




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
       "*  remove package-name: Removes given package from the clj-repo package list, " \newline
       "   must be followed by 'clj clean' and 'clj reload' to actually remove packages " \newline
       "   from the repository." \newline
       \newline
       "*  clean: Removes all packages from $CLJ_HOME/lib directory." \newline
       \newline
       "*  reload: Reloads all packages in the clj repository." \newline
       \newline
       "*  classpath: Prints classpath." \newline
       \newline
       "*  add-classpath dir-or-jar(s): Adds directories or jar files to the classpath." \newline
       "   Directories should have a trailing / to distinguish them from jar files." \newline
       \newline
       "*  add-jar jar file(s): Copies jar files to the clj repository." \newline
       "   Directories should have a trailing / to distinguish them from jar files." \newline
       \newline
       "*  remove-classpath dir-or-jar: Removes a directory or jar file from the classpath." \newline
       "   Remember to include trailing / for directories." \newline
       \newline
       "*  list-jars: Prints a list of jars in the clj repository." \newline
       \newline
       "*  help: Prints this message." \newline
       \newline
       \newline
       "Packages are installed in $CLJ_HOME/lib, and can be used by applications other " \newline
       "than clj by including the jars in that directory on the classpath. For instance, " \newline
       "to start a command line REPL with jline, run the following command: "\newline
       \newline
       "   java -cp $CLJ_HOME/lib/'*' jline.ConsoleRunner clojure.main" \newline
       \newline))


(defn clj-list-jars
  ([] (clj-list-jars (get-clj-home)))
  ([clj-home]
     (let [clj-repo (file clj-home "lib")]
       (if-not (.isDirectory clj-repo)
	 (println "The " (clj-lib-dir) " repository does not exist, needs to be initialized.")
	 (let [files (seq (.listFiles clj-repo))]
	   (println "\n\nList of jar files in the clj repository:\n")
	   (println "--------------------------------------------------------------------------------")
	   (doseq [f files] (println (.getName f)))
	   (println "\n\n"))))))


(defn initialize-classpath
  ([] (initialize-classpath (get-clj-home) (:classpath (get-project))))
  ([clj-home additional-classpaths]
     (when @classpath-uninitialized?
       (let [clj-repo (file clj-home "lib")]
	 (if-not (.isDirectory clj-repo)
	   (println "The " (clj-lib-dir) " repository does not exist, needs to be initialized.")
	   (let [additional-paths (get-classpath-urls (get-classpath-vector))
		 jar-files (seq (.listFiles clj-repo))
		 all-paths (if additional-classpaths
			     (flatten (conj jar-files additional-paths))
			     jar-files)
		 urls (map #(-> % .toURI .toURL) all-paths)
		 previous-classloader (.getContextClassLoader (Thread/currentThread))
		 current-classloader (java.net.URLClassLoader/newInstance (into-array urls) previous-classloader)]
	     (.setContextClassLoader (Thread/currentThread) current-classloader)
	     (dosync (ref-set classpath-uninitialized? false))
	     (println "Clojure classpath initialized by clj.")))))))


(defn clj-remove [library-name]
  (let [project (get-project)
	dependencies (:dependencies project)
	updated-project (assoc project :dependencies
			       (into [] (filter #(not= (symbol library-name)
						       (first %))
						dependencies)))
	proj-str (project-clj-str (:dependencies updated-project)
				  (get-classpath-vector))]
    (spit (str (get-clj-home) (sep) project-clj) proj-str)
    ;; (clj-clean)
    ;; (clj-reload)
    (println "Remember to run 'clj clean' and 'clj reload' to actually remove packages from the repo.")))


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
				#(re-find (re-pattern (str "clj(\\.|-" CLJ-VERSION "-standalone\\.|-standalone\\.)(jar|zip)")) %)
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
	   (spit (file clj-home project-clj) (project-clj-str))
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


(defn clj-add-jars [& jar-files]
  (println "Copying jar files to clj repository...")
  (let [clj-lib (file (clj-lib-dir))
	files (map file jar-files)]
    (doseq [f files]
     (copy (file f) (file clj-lib (.getName f))))))


(defn clj-clean []
  (empty-directory (file (:library-path (get-project))) true))


(defn clj-classpath []
  (println "\n\nCurrent Classpath:")
  (println "--------------------------------------------------------------------------------")
  (doseq [p (:classpath (get-project))]
    (println (str "  " p)))
  (println (str "  " (get-jars-classpath) \newline \newline)))


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


(defn clj-add-classpath
  ([& classpath]
     (let [classpath-vector (flatten (conj (get-classpath-vector) classpath))]
       ;; generate a new project.clj
       (spit (file (str (get-clj-home) (sep) project-clj))
	     (project-clj-str (get-dependencies) classpath-vector)))))


(defn clj-remove-classpath
  ([classpath]
     (let [classpath-vector (into [] (filter #(not= classpath %) (get-classpath-vector)))]
       ;; generate a new project.clj
       (spit (file (str (get-clj-home) (sep) project-clj))
	     (project-clj-str (get-dependencies) classpath-vector)))))
  

(defn abort [msg]
  (println msg)
  (System/exit 1))


(defn task-not-found [& _]
  (abort "That's not a task. Use \"clj help\" to list all tasks."))


(defn resolve-task [task]
  (let [task-ns (symbol (str "clj." task))
        task (symbol task)]
    (try
     (when-not (find-ns task-ns)
       (require task-ns))
     (or (ns-resolve task-ns task)
         #'task-not-found)
     (catch java.io.FileNotFoundException e
       #'task-not-found))))


(defn run-clj-task
  [& [task-name & args]]
  (let [task (resolve-task task-name)
               value (apply task args)]
           (when (integer? value)
             (System/exit value))))


(defn clj
  "Provides access to the clj package management system. It uses the same arguments
  as the command line version, using keywords for commands and strings for arguments."
  ([]
     (if (need-to-init?)
       (clj :self-install)
       (clj :repl)))
  ([& args]
     (initialize-classpath)
     (let [cmd (keyword (first args))
	   opts (rest args)]
       (condp = cmd
	   :self-install (clj-self-install)
	   :reload (clj-reload)
	   :list (clj-list)
	   :list-repos (clj-list-repos)
	   :install (apply clojars-install opts)
	   :remove (apply clj-remove opts)
	   :clean (clj-clean)
	   :search (apply clojars-search opts)
	   :versions (apply clojars-versions opts)
	   :describe (apply clojars-describe opts)
	   :repl (clj-repl)
	   :swingrepl (clj-repl)
	   :run (clj-run opts)
	   :classpath (clj-classpath)
	   :add-classpath (apply clj-add-classpath opts)
	   :add-jars (apply clj-add-jars opts)
	   :remove-classpath (apply clj-remove-classpath opts)
	   :list-jars (clj-list-jars)
	   :help (println (help-text))
	   (apply run-clj-task (name cmd) opts)))))


(defn -main
  ([& args]
     (apply clj args)))

