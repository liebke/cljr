(ns clj.clojars
  (:require [clojure.string :as s])
  (:use [clj internal http]
	[leiningen.deps :only (deps)]))


(def *clojars-all-jars-url* "http://clojars.org/repo/all-jars.clj")
(def *clojars-all-poms-url* "http://clojars.org/repo/all-poms.txt")
(def *clojars-repo-url* "http://clojars.org/repo/")


(defn- get-latest-version [library-name]
  (let [response (http-get-text-seq *clojars-all-jars-url*)
	lib-name (symbol library-name)]
    (second (last (filter #(= (first %) lib-name)
			  (for [line response]
			    (read-string line)))))))


(defn clojars-install
  ([library-name]
    (clojars-install library-name (get-latest-version library-name)))
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
     (str *clojars-repo-url* file-location)))


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


(defn clojars-describe
  ([library-name]
     (clojars-describe library-name (get-latest-version library-name)))
  ([library-name version]
     (let [pom-xml-seq (get-latest-pom-file library-name version)
	   desc-text (description-text pom-xml-seq)]
       (println (str "\n\nDescription for library: " library-name "  " version))
       (println "--------------------------------------------------------------------------------")
       (println desc-text)
       (println "\n\n"))))


