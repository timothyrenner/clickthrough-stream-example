(ns clickthrough-kafka.core
    (:require [clojure.core.async :as async 
                :refer [chan go <! >! timeout]]
              [clj-kafka.new.producer :as kafka 
                :refer [producer send record byte-array-serializer]]
              [clj-uuid :as uuid])
  (:gen-class))

(defn -main
  "Executes the \"simulation\" with ten different ads, and writes to Kafka."
  [& args]
  (with-open [p (producer {"bootstrap.servers" "127.0.0.1:9092"}
                          (byte-array-serializer)
                          (byte-array-serializer))]
                           
    (let [impression-chan (chan 1)
          click-chan (chan 1)]
      
      ;; Set up a background thread to read the impressions.
      (go (while true 
              (let [in (<! impression-chan)]
                   (send p 
                      (record "impressions" 
                        (.getBytes (str (first in) "," (second in))))))))
      ;; Set up a background thread to read the clicks.
      (go (while true
              (let [in (<! click-chan)]
                   (send p
                      (record "clicks"
                        (.getBytes (str (first in) "," (second in))))))))
    
      ;; Main loop for generating events.
      (while true
         (Thread/sleep 100) ; Throttle the events to prevent out of memory.
         ;; Get a UUID for the user and an integer for the ad ID.
         (let [id (uuid/v4)
               ad-id (rand-int 10)]
             ;; Send the impression.
             (go (>! impression-chan [id ad-id]))
             ;; Send the click event if rand is less than 0.2
             ;; (20% clickthrough per ad on average). Send on a background 
             ;; thread that "waits"a certain number of seconds between 
             ;; 10 and 20.
             (when (< (rand) 0.2)
                   (go 
                      ;; Sleep ten seconds + 0-10 additional seconds.
                      (<! (timeout  (+ 10000 (* 1000 (rand-int 10)))))
                      (>! click-chan [id ad-id]))))))))
