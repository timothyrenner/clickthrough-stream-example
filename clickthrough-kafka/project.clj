(defproject clickthrough-kafka "0.1.0-SNAPSHOT"
  :description "Basic simulator for streaming clickthrough examples."
  :url "http://www.github.com/timothyrenner/clickthrough-stream-example"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-kafka "0.3.4"]
                 [org.clojure/core.async  "0.2.374"]
                 [danlentz/clj-uuid "0.1.6"]]
  :main ^:skip-aot clickthrough-kafka.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
