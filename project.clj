(defproject monitoring "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [clj-http "3.6.1"]
                 [riemann-clojure-client "0.4.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.apache.logging.log4j/log4j-api "2.9.1"]
                 [org.apache.logging.log4j/log4j-core "2.9.1"]
                 [org.apache.logging.log4j/log4j-jcl "2.9.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.9.1"]
                 [metrics-clojure "2.10.0"]
                 ]
  :main ^:skip-aot monitoring.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
