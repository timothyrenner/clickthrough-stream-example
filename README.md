# Description

This project is a collection of streaming data solutions for calculating clickthroughs for an online advertising campaign simulator (also included).
Currently the solutions implemented are:

* Storm (Clojure DSL)
* Spark Streaming (Scala)

The simulation is implemented in Clojure and writes to two Kafka topics: `impressions` and `clicks`.
The solutions read from those topics and compute the streaming clickthrough.

Since these solutions are designed to be illustrative examples, they're designed to run in local mode.
I'm assuming if you plan on running these on a cluster you'll know how to change the code accordingly :).

Each of these solutions is it's own subproject, and has it's own detailed README. 
This README is just a high level view of the project and a quick start guide to get going right away.

### Versions
Tested on the following versions:

* Kafka 0.9 for Scala 2.11
* Storm 0.10.0
* Spark 1.6.0 for Scala 2.10

### Compilers/Build Tools
The Clojure Storm solution uses [Leiningen](http://leiningen.org/).
The Scala Spark Streaming Solution uses [sbt](http://www.scala-sbt.org/).

# Quick Start

You'll need four terminals open.
In each of the terminals, set the following environment variables:

```bash
KAFKA_HOME=/path/to/kafka # ~/kafka/kafka_2.11-0.9.0.0 on my system
SPARK_HOME=/path/to/spark # ~/spark/spark-1.6.0-bin-hadoop2.6 on my system
```

In the first terminal, navigate to the `clickthrough-kafka-clj` directory and start Zookeeper with 

```bash
dev-resources/start-zookeeper.sh
```

In the second terminal, navigate to the `clickthrough-kafka-clj` directory and start Kafka with 

```bash
dev-resources/start-kafka.sh
```

In the third terminal, navigate to the `clickthrough-kafka-clj` directory and create the Kafka topics with

```bash
dev-resources/create-kafka-topics.sh
```

After the topcis are created, start the simulation with 

```bash
lein run
```

After a few moments (maybe more than a few, Clojure devs know what I'm talking about) the simulation will start writing to Kafka.
First time running it will result in a few downloads.

Once you've got Zookeeper, Kafka, and the simulation running, start one of the following solutions:

## Spark Streaming

The Spark Streaming solution needs to be built first.
Navigate to the `clickthrough-spark` directory and build the fat jar with

```bash
sbt assembly
```
After a few moments you'll have your fat jar in `target/scala-2.10/`.
Don't worry about moving it, there's a script in `dev-resources` that knows where it is and submits it to Spark for you.

Once you have your fat jar, run it with

```bash
dev-resources/submit-spark-job.sh
```

You'll probably want to redirect the output to a file since Spark's pretty chatty.

## Storm

The Storm solution can just run right away.
Navigate to the `clickthrough-storm-clj` directory and run

```bash
lein run
```

You'll probably want to redirect the output to a file since Storm is also quite chatty.
It's configured to log to STDERR.