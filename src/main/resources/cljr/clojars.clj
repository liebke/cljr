(ns cljr.clojars
  (:require [clojure.string :as s])
  (:use [cljr core http]
	[leiningen.deps :only (deps)]))


(def *clojars-repo-url* "http://clojars.org/repo")
(def *clojars-all-jars-url* (str *clojars-repo-url* "/all-jars.clj"))
(def *clojars-all-poms-url* (str *clojars-repo-url* "/all-poms.txt"))



(defn get-latest-version [library-name]
  (let [response (http-get-text-seq *clojars-all-jars-url*)
	lib-name (symbol library-name)]
    (second (last (filter #(= (first %) lib-name)
			  (for [line response]
			    (read-string line)))))))


(defn clojars-install
  ([library-name]
     (let [version (get-latest-version library-name)]
       (if version
	 (clojars-install library-name version)
	 (println "Cannot find version of" library-name "on Clojars.org.\r\n"
		  "If the library is in another repository, provide a version argument."))))
  ([library-name library-version]
    (let [project (get-project)
	  dependencies (:dependencies project)
	  updated-project (assoc project :dependencies
				 (conj dependencies
				       [(symbol library-name)
					library-version]))
	  proj-str (project-clj-string updated-project
				       {:dependencies (:dependencies updated-project)})]
      (println "Installing version " library-version " of " library-name "...")
      (spit (str (get-cljr-home) (sep) project-clj) proj-str)
      (deps (get-project)))))


(defn clojars-search [term]
  (let [response (http-get-text-seq *clojars-all-jars-url*)]
    (println "\n\nLibraries on Clojars.org that contain the term: " term)
    (println "--------------------------------------------------------------------------------")
    (doseq [entry (for [line response :when (.contains line term)]
		    (read-string line))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))


(defn clojars-versions [library-name]
  (let [response (http-get-text-seq *clojars-all-jars-url*)]
    (println "\n\nAvailable versions for library: " library-name)
    (println "--------------------------------------------------------------------------------")
    (doseq [entry (filter #(= (first %) (symbol library-name))
			  (for [line response]
			    (read-string line)))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))


(defn get-pom-dir
  ([library-name version]
     (let [id-str (if (.contains library-name "/")
		    (str "./" library-name "/" version "/")
		    (str "./" library-name "/" library-name "/" version "/"))]
       id-str)))


(defn get-pom-locations
  ([library-name version]
     (let [response (http-get-text-seq *clojars-all-poms-url*)
	   pom-dir (get-pom-dir library-name version)]
       (for [line response :when (.startsWith line pom-dir)]
	 line))))


(defn get-latest-pom-location
  ([library-name version]
     (last (get-pom-locations library-name version))))


(defn to-clojars-url
  ([file-location]
     (str *clojars-repo-url* "/" file-location)))


(defn get-latest-pom-file
  ([library-name version]
     (http-get-text-seq (to-clojars-url (get-latest-pom-location library-name version)))))


(defn extract-description-text [xml]
  (when-let [desc (re-find (re-pattern (str "<description>(.*)</description>")) xml)]
    (second desc)))


(defn description-text [xml-seq]
  (-> (apply str
	     (for [line xml-seq
		   :when (.contains line "<description>")]
	       line))
      (extract-description-text)))


(defn get-library-dependencies
  [library-name version]
  (let [pom-xml (clojure.xml/parse
		 (java.io.ByteArrayInputStream.
		  (.getBytes (apply str (get-latest-pom-file library-name version)))))
	deps-xml (filter #(= (:tag %) :dependencies) (:content pom-xml))
	deps-seq (partition 3 (for [x (xml-seq deps-xml)
				    :when (or (= (:tag x) :artifactId)
					      (= (:tag x) :groupId)
					      (= (:tag x) :version))] 
				(hash-map (:tag x) (first (:content x)))))]
    (into #{} (for [v deps-seq] (apply merge v)))
    (map :content (:content (first deps-xml)))))


(defn print-library-dependencies
  [library-name version]
  (println (str "\n\nDependencies for: " library-name "  " version))
  (println "--------------------------------------------------------------------------------")
  (doseq [d (get-library-dependencies library-name version)]
    (let [dep (apply merge (map #(hash-map (:tag %) (first (:content %))) d))]
      (println (str (:groupId dep) "/" (:artifactId dep) "  " (:version dep)))))
  (println "\n\n"))


(defn clojars-describe
  ([library-name]
     (clojars-describe library-name (get-latest-version library-name)))
  ([library-name version]
     (let [pom-xml-seq (get-latest-pom-file library-name version)
	   desc-text (description-text pom-xml-seq)]
       (println (str "\n\nDescription for library: " library-name "  " version))
       (println "--------------------------------------------------------------------------------")
       (println desc-text)
       (println "")
       (when desc-text
	 (print-library-dependencies library-name version)))))


