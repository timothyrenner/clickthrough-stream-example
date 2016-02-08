(ns clickthrough-storm-clj.core
  (:import [backtype.storm StormSubmitter LocalCluster]
           [storm.kafka KafkaConfig ZkHosts 
                        KafkaSpout SpoutConfig StringScheme]
           [backtype.storm.spout SchemeAsMultiScheme]
           [kafka.api OffsetRequest])
  (:require [clojure.string :as string]
            [backtype.storm.clojure :refer [topology spout-spec bolt-spec bolt
                                            defspout defbolt emit-bolt! ack!]]
            [backtype.storm.config :refer [TOPOLOGY-DEBUG TOPOLOGY-WORKERS]]
            [com.github.kyleburton.clj-bloom :as bf])
  (:gen-class))

;; NOTE: Assumes local mode for Kafka (means Zookeeper is running at 
;;                                             localhost:2181).
(def kafka-zk-hosts (ZkHosts. "localhost:2181"))

(defn spout-config 
  " Creates the spout configuration from the topic, zookeeper root, and the
    spout name.
    
    topic: The name of the topic.
    
    zk-root: A path on zookeeper to store the offsets (must be unique - learn
             from my pain), and must start with \"/\"
    
    spout-name: A unique identifier for the spout.
  "
  [topic zk-root spout-name]
  (let [cfg (SpoutConfig. kafka-zk-hosts topic zk-root spout-name)]
       (set! (. cfg scheme) (SchemeAsMultiScheme. (StringScheme.)))
       (set! (. cfg startOffsetTime) (. OffsetRequest LatestTime))
       cfg))

;; Spouts.     
(def impression-spout 
  (KafkaSpout. (spout-config "impressions" "/ct-im" "impression-spout")))

(def click-spout
  (KafkaSpout. (spout-config "clicks" "/ct-cl" "click-spout")))

;; Split Impressions.
(defbolt split-impressions ["user-id" "ad-id" "type"] [tuple collector]
         (let [[user-id ad-id] 
              ;; Action is here, just a string/split call.
              (string/split (.getString tuple 0) #",")]
              (emit-bolt! collector [user-id ad-id "impression"] :anchor tuple))
            (ack! collector tuple))
          
;; Split clicks.
(defbolt split-clicks ["user-id" "ad-id" "type"] [tuple collector]
         (let [[user-id ad-id] 
              ;; Action is here, just a string/split call.
              (string/split (.getString tuple 0) #",")]
              (emit-bolt! collector [user-id ad-id "click"] :anchor tuple))
            (ack! collector tuple))
          
;; Join impressions and clicks.
;; Note that this solution is not "industrial strength" - the bloom filters 
;; will saturate if this topology runs too long. A production solution would
;; track their saturations and swap them out, or use a time-decaying bloom
;; filter variation.
(defbolt impression-click-join ["ad-id" "clicked"]
         {:prepare true} ; This indicates this is a stateful bolt.
         [conf context collector]
         (let [
               ;; Bloom filter with 10 hashes that saturates at 100k elements.
               impression-filter (bf/make-optimal-filter 100000 0.01)
               click-filter (bf/make-optimal-filter 100000 0.01)]
            (bolt
              (execute [tuple]
                (let [user-id (.getString tuple 0)
                      ad-id (.getString tuple 1)
                      action (.getString tuple 2)]
                    ;; Perform the join.
                    (case action
                          "impression"
                            (do (emit-bolt! collector [ad-id false] :anchor tuple)
                                ;; Check if we've seen a click from this user.
                                (if (bf/include? click-filter user-id)
                                    ;; Hooray! Emit a completed click.
                                    (emit-bolt! collector [ad-id true] 
                                                :anchor tuple)
                                    ;; Oh no! Save the user id in the impression
                                    ;; set.
                                    (bf/add! impression-filter user-id)))
                          "click"
                          ;; Check if we've seen an impression from this user.
                          (if (bf/include? impression-filter user-id)
                              ;; Hooray! Emit a completed click.
                              (emit-bolt! collector [ad-id true]
                                          :anchor tuple)
                              ;; Oh no! Save the user id in the click set.
                              (bf/add! click-filter user-id))))
                  (ack! collector tuple)))))
  
  ;; Calculate the cumulative moving average of the clicks and impressions.
  ;; This one works by creating a "click-value" which is 1 if clicked, 0 if not,
  ;; and an "impression-value" which is the opposite. The strategy is to take
  ;; the moving average of the click and impression event indicators separately,
  ;; then compute the clickthrough from the averages.
(defbolt calculate-clickthrough ["ad-id" "clickthrough"]
    {:prepare true :params [alpha]} ; Stateful and parameterized.
    [conf context collector]
      (let [impression-ma (atom {})
            click-ma (atom {})]
            (bolt
              (execute [tuple]
                (let [ad-id (.getString tuple 0)
                      action (.getBoolean tuple 1)
                      ;; click-value and impression-value are indicators for
                      ;; whether we saw a click or an impression. Both are
                      ;; used in the clickthrough calculation.
                      click-value (if action 1.0 0.0)
                      impression-value (if action 0.0 1.0)]
                  ;; Update the moving average for the click events.
                  (swap! click-ma assoc
                         ;; alpha * click-value + (1 - alpha) * click-average
                         ad-id (+ (* alpha click-value) 
                                  (* (- 1 alpha) (@click-ma ad-id 0.0))))
                  ;; Update the moving average for the impression events.
                  (swap! impression-ma assoc
                         ;; alpha * imp-value + (1 - alpha) * imp-value
                         ad-id (+ (* alpha impression-value)
                                  (* (- 1 alpha) (@impression-ma ad-id 0.0))))
                  ;; Calculate and emit average clickthrough.
                  (emit-bolt! collector 
                              [ad-id (/ (@click-ma ad-id) 
                                        (@impression-ma ad-id))]
                              :anchor tuple))
                (ack! collector tuple)))))
  
;; This bolt just prints to the console.
(defbolt echo [] [tuple collector]
    (let [ad-id (.getString tuple 0)
          clickthrough (.getDouble tuple 1)]
      ;; Action is here; println.
      (println "Ad: " ad-id " Clickthrough: " clickthrough))
    (ack! collector tuple))
                     
(defn make-topology [alpha]
  (topology
    ;; Spouts come first. :p specifies parallelism.
    {"impression-spout" (spout-spec impression-spout :p 1)
     "click-spout" (spout-spec click-spout :p 1)}
    ;; Now come the bolts. Map after bolt-spec is the input stream/grouping
    ;; declaration.
    {"split-impressions" (bolt-spec {"impression-spout" :shuffle}
                                    split-impressions
                                    :p 1)
     "split-clicks" (bolt-spec {"click-spout" :shuffle}
                               split-clicks
                               :p 1)
     ;; This one's a fields grouping by "user-id" for both input streams.
     ;; The state in the bolt requires the same user ID from impressions
     ;; and clicks end up on the same bolt instance.
     "join-impressions-clicks" (bolt-spec {"split-impressions" ["user-id"]
                                           "split-clicks" ["user-id"]}
                                           impression-click-join
                                           :p 1)
     ;; Same as the join bolt for the fields grouping - we need the same ad IDs
     ;; to end up on the same bolt instances since we're storing them.
     "calculate-clickthrough" (bolt-spec {"join-impressions-clicks" ["ad-id"]}
                                         (calculate-clickthrough alpha)
                                         :p 1)
    "print" (bolt-spec {"calculate-clickthrough" :shuffle} 
                       echo 
                       :p 1)}))
  
(defn -main
  "Runs the Storm topology in local mode for computing streaming clickthroughs.
  Requires ZooKeeper running at localhost:2181 brokering the Kafka cluster.
  The Kafka cluster needs two topics: 'impressions' and 'clicks'. Takes an 
  optional integer argument for the number of seconds to run the topology 
  (default: 180)."
  ([] (-main 180))
  ([& args]
  (let [run-seconds (int (first args))
        cluster (LocalCluster.)]
      (.submitTopology cluster 
         "clickthrough-topology"
         ;; For some reason, the TOPOLOGY-DEBUG configuration doesn't work, even
         ;; when it's true. Logging is set through log4j.properties in
         ;; resources/.
         {TOPOLOGY-DEBUG false}
         (make-topology 0.001))
      (Thread/sleep (* run-seconds 1000))
      (.shutdown cluster))))
