(ns cljr.scripts
  (:use [cljr core]))

(defn cygwin-safe-path-sep []
  (if (= ";" (path-sep))
    "\\;"
    (path-sep)))


(defn cljr-sh-script
  ([]
     (str "#!/bin/sh\n\n"
	  "USER_HOME=\"" (get-user-home) "\"\n"
	  "CLJR_HOME=\"" (get-cljr-home) "\"\n" 
	  "CLASSPATH=src" (cygwin-safe-path-sep) "test" (cygwin-safe-path-sep) "." (cygwin-safe-path-sep) (get-cljr-home) "\n\n"

	  "   if [ ! -n \"$JVM_OPTS\" ]; then\n"
	  "      JVM_OPTS=\"-Xmx1G\"\n"
	  "   fi\n\n"

	  "   if [ \"$DISABLE_JLINE\" = \"true\" ]; then\n"
	  "      JLINE=\"\"\n"
	  "   else\n"
	  "      JLINE=\"jline.ConsoleRunner\"\n"
	  "   fi\n\n"

	  "if [ \"$1\" = \"repl\" -o \"$1\" = \"swingrepl\" -o \"$1\" = \"swank\" -o \"$1\" = \"run\" ]; then\n"
	  "   if [ -n \"$CLOJURE_HOME\" ]; then\n"
	  "      for f in \"$CLOJURE_HOME\"/*.jar; do\n"
	  "         CLASSPATH=\"$CLASSPATH\"" (cygwin-safe-path-sep) "$f\n"
	  "      done\n"
	  "   fi\n"
	  "   for f in \"$CLJR_HOME\"/lib/*.jar; do\n"
	  "      CLASSPATH=\"$CLASSPATH\"" (cygwin-safe-path-sep) "$f\n"
	  "   done\n\n"
	  
	  "   if [ \"$1\" = \"repl\" ]; then\n"
	  "      java $JVM_OPTS -Dinclude.cljr.repo.jars=false -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" -cp \"$CLASSPATH\" $JLINE clojure.main -e \"(require 'cljr.main) (cljr.main/initialize-classpath)\" -r\n"
	  "   else\n"
	  "      java $JVM_OPTS -Dinclude.cljr.repo.jars=false -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" -cp \"$CLASSPATH\" cljr.App $*\n" 
	  "   fi\n"
	  "else\n"
	  "   CLASSPATH=\"$CLASSPATH\"" (cygwin-safe-path-sep) "\"$CLJR_HOME\"/cljr.jar\n"
	  "   java $JVM_OPTS -Duser.home=\"$USER_HOME\" -Dclojure.home=\"$CLOJURE_HOME\" -Dcljr.home=\"$CLJR_HOME\" -cp \"$CLASSPATH\" cljr.App $*\n" 
	  "fi\n\n")))


(defn cljr-bat-script
  ([]
     (str "@echo off\r\n"
     "setLocal EnableDelayedExpansion\r\n\r\n"
     "set CLJR_HOME=\"" (get-cljr-home) "\"\r\n"
     "set USER_HOME=\"" (get-user-home) "\""
     "\r\n\r\n"
     
     "if not defined \"%CLOJURE_HOME%\" set CLOJURE_HOME=\"\""
     "\r\n"
     "if not defined \"%JVM_OPTS%\" set JVM_OPTS=-Xmx1G"
     "\r\n\r\n"
     
     "if (%1) == (repl) goto SET_CLOJURE_JARS\r\n"
     "if (%1) == (swingrepl) goto SET_CLOJURE_JARS\r\n"
     "if (%1) == (swank) goto SET_CLOJURE_JARS\r\n"
     "if (%1) == (run) goto SET_CLOJURE_JARS\r\n"
     "if (%1) == () goto SET_CLOJURE_JARS\r\n\r\n"

     "goto LAUNCH_CLJR_ONLY\r\n\r\n"

     ":SET_CLOJURE_JARS\r\n"
     "     if not defined %CLOJURE_HOME% goto SET_CLASSPATH\r\n"
     "     set CLASSPATH=\"\r\n"
     "        for /R %CLOJURE_HOME% %%a in (*.jar) do (\r\n"
     "           set CLASSPATH=!CLASSPATH!;%%a\r\n"
     "        )\r\n"
     "        set CLASSPATH=!CLASSPATH!\"\r\n"
     "goto SET_CLASSPATH\r\n\r\n"

     ":SET_CLASSPATH\r\n"
     "  set CLASSPATH=\"\r\n"
     "     for /R \"" (get-cljr-home) "\\lib\" %%a in (*.jar) do (\r\n"
     "        set CLASSPATH=!CLASSPATH!;%%a\r\n"
     "     )\r\n"
     "     set CLASSPATH=!CLASSPATH!\"\r\n"
     "  set CLASSPATH=%CLASSPATH%;src;test;.;" (get-cljr-home) "\r\n"
     "goto LAUNCH\r\n\r\n"

     ":LAUNCH_CLJR_ONLY\r\n"
     "  java %JVM_OPTS% -Dcljr.home=" (get-cljr-home) " -Duser.home=" (get-user-home) " -jar \"" (get-cljr-home) "\\cljr.jar\" %*\r\n"
     "goto EOF\r\n\r\n"

     ":LAUNCH\r\n"
     "  java %JVM_OPTS% -Dinclude.cljr.repo.jars=false -Dcljr.home=" (get-cljr-home) " -Duser.home=" (get-user-home) " -Dclojure.home=%CLOJURE_HOME% -cp \"%CLASSPATH%\" cljr.App %*\r\n"
     "goto EOF\r\n\r\n"
     
     ":EOF\r\n")))
