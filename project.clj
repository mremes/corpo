(defproject corpo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.10.0"]
                 [metosin/jsonista "0.2.5"]
                 [org.clojure/tools.namespace "0.3.1"]
                 [org.clojure/core.async "0.6.532"]
                 [slingshot "0.12.2"]
                 [clj-time "0.15.2"]
                 [org.clojure/java.jdbc "0.7.10"]
                 [org.xerial/sqlite-jdbc "3.28.0"]]
  :repl-options {:init-ns corpo.core}
  :main corpo.core)
