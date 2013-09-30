(defproject theclaw "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [server-socket "1.0.0"]
                 [compojure "1.1.5"]
                 [ring-server "0.2.8"]
                 [hiccup "1.0.4"]
                 [org.clojure/tools.nrepl "0.2.3" :exclusions [org.clojure/clojure]]
                 [com.pi4j/pi4j-core "0.0.5"]]
  :main theclaw.core)
