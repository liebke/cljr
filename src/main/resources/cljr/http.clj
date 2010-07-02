(ns cljr.http
  (:import [java.net URL HttpURLConnection]
	   [java.io StringReader])
  (:require [clojure.contrib.duck-streams :as duck]
	    [clojure.string :as s]))


(def *connect-timeout* 0)


(defn- body-seq
  "Returns a lazy-seq of lines from either the input stream
   or the error stream of connection, whichever is appropriate."
  [^HttpURLConnection connection]
  (duck/read-lines (or (if (>= (.getResponseCode connection) 400)
                         (.getErrorStream connection)
                         (.getInputStream connection))
                       (StringReader. ""))))


(defn get-http-connection
  ([url]
     (let [u (URL. url)
	   ^HttpURLConnection connection (cast HttpURLConnection (.openConnection u))
	   method "GET"]
       (.setRequestMethod connection method)
       (.setConnectTimeout connection *connect-timeout*)
       connection)))



(defn http-get-text-seq
  ([url]
     (body-seq (get-http-connection url))))



(defn set-system-proxy!
  "Java's HttpURLConnection cannot do per-request proxying. Instead,
  system properties are used. This function mutates the global setting.
  For per-request proxying, use the Apache HTTP client."
  [#^String host port]
  (doto (System/getProperties)
    (.setProperty "http.proxyHost" host)
    (.setProperty "http.proxyPort" (str port)))
  nil)


