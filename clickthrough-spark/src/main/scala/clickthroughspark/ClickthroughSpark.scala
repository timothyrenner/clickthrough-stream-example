package clickthroughspark {
    
    import org.apache.spark.streaming.{StreamingContext, Seconds}
    import org.apache.spark.SparkContext._
    import org.apache.spark.SparkConf
    
    import org.apache.spark.streaming.kafka._
    
    import kafka.serializer.StringDecoder
    
    /** Computes a rolling 30-second window with a five second slide against
     *  simulated clickstream data stored in Kafka. Here are the assumptions
     *  to get this running:
     * 
     *  - Kafka running at localhost:9092
     *  - Two Kafka topics: 'impressions' and 'clicks'
     */
    object ClickthroughSpark {
        
        /** Runs the Spark Streaming job.
         * 
         * @param args: The time to run the job in seconds (default: 180).
         */
        def main(args: Array[String]) = {
            
            // Parse out the command line arguments.
            val runTimeInSeconds = if(args.length > 0) args(1).toInt else 180
            
            // Set up the application and stream. The stream will read in 1
            // second micro batches.
            val sparkConf = new SparkConf().setAppName("Clickthrough")
            val ssc = new StreamingContext(sparkConf,Seconds(1))
            
            // Set the checkpoint directory, which is required for the
            // countByValueAndWindow call we're going to make. Since this is
            // just an example, the following solution is not "industrial 
            // strength" since we aren't providing a means to recover the
            // context from the checkpoint.
            ssc.checkpoint("./checkpoints")
            
            // Create the direct kafka stream with the brokers and topics.
            // We assume Kafka is running at localhost:9092.
            val kafkaParams = 
                Map[String, String]("metadata.broker.list" -> "localhost:9092")
            
            val impressionStream = 
                KafkaUtils.createDirectStream[String, String, 
                                              StringDecoder, StringDecoder](
                    ssc, kafkaParams, Set("impressions"))
            
            val clickStream = 
                KafkaUtils.createDirectStream[String, String,
                                              StringDecoder, StringDecoder](
                    ssc, kafkaParams, Set("clicks"))
            
            // Aggregate the impressions by ad ID within a thirty second window
            // with a five second slide.
            val impressionCountsByAd = 
                impressionStream.map{ case(k, v) => v.split(",")(1) }
                                .countByValueAndWindow(Seconds(60),
                                                       Seconds(5))
            val clickCountsByAd =
                clickStream.map{ case(k,v) => v.split(",")(1) }
                           .countByValueAndWindow(Seconds(60),
                                                  Seconds(5))
            
            // Now join the windowed ads and calculate clickthrough.
            val clickthroughs = 
                impressionCountsByAd.leftOuterJoin(clickCountsByAd)
                                    .mapValues{ 
                                        case(i, c) => {
                                            val clCount = c match {
                                                case Some(v) => v.toDouble
                                                case None => 0.0 }
                                            clCount/i.toDouble }}
            
            // Finally, write to the console.
            clickthroughs.foreachRDD{ 
                _.foreach{ 
                    case(a,ct) => println(s"Ad: $a Clickthrough: $ct") }}
            
            ssc.start()
            
            // Terminate after 100 seconds, just like the Storm example.
            Thread.sleep(runTimeInSeconds * 1000)
            ssc.stop(true, true)
        } // Close main.
    } // Close ClickthroughSpark.
} // Close clickthroughSpark.