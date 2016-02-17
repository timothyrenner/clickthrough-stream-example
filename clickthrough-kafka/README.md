# Ad Campaign Simulator

This program simulates an online advertising campaign by generating impression events (views) and click events, writing them to Kafka.

The project needs to be built with [Leiningen](http://leiningen.org/).

## Launching
To start, you'll need three terminals open and an environment variable, `KAFKA_HOME`, that points to the root of the Kafka installation in each terminal.
Here's what it is on my system:

```bash
KAFKA_HOME=~/kafka/kafka_2.11-0.9.0.0
```

In terminal 1, start Zookeeper from the project root with the following script

```bash
dev-resources/start-zookeeper.sh
```

In terminal 2, start Kafka from the project root with the following script

```bash
dev-resources/start-kafka.sh
```

In terminal 3, first create the Kafka topics with the following script

```bash
dev-resources/create-kafka-topics.sh
```

Then fire up the simulator

```bash
lein run
```

## Internals

The simulator is pretty simple.
It creates a stream of impression events with UUIDs, then randomly selects a certain few to generate a click event after a randomized timeout period between 10 and 20 seconds.
The "rate" for generating clicks is 0.2, so that's the intrinsic clickthrough rate for the ads.
This can be easily changed in the code as follows:

```clojure
(while true

	(Thread/sleep 100)
	
	(let [id (uuid/v4)
		   ad-id (rand-int 10)]
		   
		(go (>! impression-chan [id ad-id]))
		
		(when (< (rand) 0.2) ;; <- CLICKTHROUGH
			(go
				;; ...))))
```

The clicks and impressions get written each to a kafka topci of those names.
It's all done asynchronously, so impressions will continue to stream while the timeout period for the related click event is happening.

The impression event generator is intentionally throttled at 10 events / sec.
Here's the code that performs the throttling:

```clojure
(while true
	(Thread/sleep 100) ;; <- THROTTLE VALUE
	;; Generate impression and maybe a click.
	;; ...)
```

The throttling can be adjusted by changing the sleep period.

Finally, the Kafka information is hard coded for local mode.
You can change it in the following piece of code:

```clojure
(with-open 
	[p (producer {"bootstrap.servers" 
		     		 "127.0.0.1:9092"} ;; <- LOCAL HARDCODE
	 			    (byte-array-serializer)
	 			    (byte-array-serializer))]
	 ;; Do stuff
	 ;; ... ))
```