package cljr;

import clojure.lang.RT;

public class App {

  private static final String MAINCLJ = "cljr/main.clj";
 
  public static void main(String[] args) {
    try {
          RT.loadResourceScript(MAINCLJ);
          RT.var("cljr.main", "-main").invoke(args);
        } catch(Exception e) {
          e.printStackTrace();
        }
    }
}