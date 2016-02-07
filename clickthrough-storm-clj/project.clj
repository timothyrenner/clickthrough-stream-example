(defproject clickthrough-storm-clj "0.1.0-SNAPSHOT"
  :description "Storm topology for computing streaming clickthrough from the 
                simulator output Kafka topics."
  :url "http://www.github.com/timothyrenner/clickthrough-stream-example"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"
            :year 2016
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.apache.storm/storm-core "0.10.0"]
                 [org.apache.storm/storm-kafka "0.10.0"]
                 [org.apache.kafka/kafka_2.11 "0.9.0.0"]
                 [com.github.kyleburton/clj-bloom "1.0.4"]]
  :main ^:skip-aot clickthrough-storm-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
