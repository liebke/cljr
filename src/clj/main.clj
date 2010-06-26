(ns clj.main
  (:use [leiningen.core :only (read-project)]
        [leiningen.deps :only (deps)]
	[leiningen.clean :only (empty-directory)]
	[clojure-http.client :only (request)]
        [clojure.java.io :only (file copy)]
        [swank.swank :only (start-repl)])
  (:require org.dipert.swingrepl.main
	    [clojure.string :as s])
  (:gen-class))


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
			['clojure-http-client "1.1.0-SNAPSHOT"]
                        ['swank-clojure "1.2.1"]])


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
       "*  swank [port]: Start a local swank server on port 4005 (or as specified)."
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
       "*  add-classpath dir-or-jar: Adds directory or jar file to the classpath." \newline
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
	  "-Dclj.home=$CLJ_HOME jline.ConsoleRunner clojure.main "
	  " -e \"(require 'clj.main) (clj.main/initialize-classpath)\" -r "
	  "\n" 
	  "else \n"
	  "   java -cp \"" (get-clj-home) (sep) clj-jar "\" "
	  "-Duser.home=\"" (get-user-home) "\" -Dclj.home=$CLJ_HOME clj.main $* \n"
	  "fi \n")))


(defn clj-bat-script
  ([]
     (let [clj-home (get-clj-home)]
       (str "@echo off\r\n"
	    "java -cp \"" (get-clj-home) (sep) clj-jar "\" "
	    "-Duser.home=" (get-user-home) " -Dclj.home=" (get-clj-home)
	    " clj.main %* \r\n"))))


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
	  proj-str (project-clj-str (:dependencies updated-project)
				    (get-classpath-vector))]
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
	proj-str (project-clj-str (:dependencies updated-project)
				  (get-classpath-vector))]
    (spit (str (get-clj-home) (sep) project-clj) proj-str)
    ;; (clj-clean)
    ;; (clj-reload)
    (println "Remember to run 'clj clean' and 'clj reload' to actually remove packages from the repo.")))


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
     (let [classpath-vector (conj (get-classpath-vector) classpath)]
       ;; generate a new project.clj
       (spit (file (str (get-clj-home) (sep) project-clj))
	     (project-clj-str (get-dependencies) classpath-vector)))))


(defn clj-remove-classpath
  ([classpath]
     (let [classpath-vector (into [] (filter #(not= classpath %) (get-classpath-vector)))]
       ;; generate a new project.clj
       (spit (file (str (get-clj-home) (sep) project-clj))
	     (project-clj-str (get-dependencies) classpath-vector)))))
  

(defn clj-swank
  ([]
     (clj-swank 4005))
  ([port]
     (cond
      (integer? port) (start-repl port)
      (string? port) (start-repl (Integer/parseInt port 10))
      :else (println "Invalid port number."))))


(defn clj
  "Provides access to the clj package management system. It uses the same arguments
  as the command line version, using keywords for commands and strings for arguments."
  ([]
     (if (need-to-init?)
       (clj :self-install)
       (clj :repl)))
  ([command & args]
     (initialize-classpath)
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
	   :classpath (clj-classpath)
	   :add-classpath (apply clj-add-classpath args)
	   :remove-classpath (apply clj-remove-classpath args)
	   :list-jars (clj-list-jars)
           :swank (apply clj-swank args)
	   :help (println (help-text))
	   (println "unrecognized command to clj.")))))


(defn -main
  ([& args]
     (apply clj args)))

