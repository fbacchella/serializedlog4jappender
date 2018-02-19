SerializedAppender
===========

This module provides different appender that send serialized events instead of String, like that's possible in log4j2.

It serialize the Log4j events in native java format, to avoid inefficient common formats like json and allow to keep all the events data as is. It can also uses [msgpack](https://msgpack.org).

It provides two possible destination, that are [Apache Kafka](https://kafka.apache.org) or [ØMQ](http://zeromq.org)
 * common options are
   * hostname: it will be added as a `hostname` property, default to the value of `java.net.InetAddress.getLocalHost().getHostName()`.
   * locationInfo: true of false, it will send or not the log event location (file, line, method), default to false.
   * application: the application name, it's optionnal.
   * serializer: a class used to serialize Log4j's events to a byte array. It must implements loghub.log4j.Serializer class and provide a constructor with no arguments.

Two serializers are provided: loghub.log4j.JavaSerializer (using native java serialization) and loghub.log4j.MsgPackSerializer (serialized to a msgpack object, as a map)

## KafkaAppender

A [Apache Kafka](https://kafka.apache.org) Appender for log4j.

To install it, just run `mvn package` and add `target/kafkalogappender.jar to your project's classpath. The default configuration of method and type should fit common usage where logs are send to kafka cluser, so only brokers should be changed.

Many options can be changed :

 * The options tied to Kafka are
   * brokerList
   * topic
   * compressionType
   * securityProtocol
   * sslTruststoreLocation
   * sslTruststorePassword
   * sslKeystoreType
   * sslKeystoreLocation
   * sslKeystorePassword
   * saslKerberosServiceName
   * clientJaasConfPath
   * kerb5ConfPath

A sample declaration is:

    log4j.appender.A1=loghub.log4j.KafkaAppender
    log4j.appender.A1.brokerList=127.0.0.1:9093
    log4j.appender.A1.topic=test-topic
    log4j.appender.A1.requiredNumAcks=1
    log4j.appender.A1.syncSend=false
    log4j.appender.A1.locationInfo=true
    log4j.appender.A1.serializer=loghub.log4j.JavaSerializer
    log4j.appender.A1.application=some_application_name
    log4j.rootLogger=TRACE, A1

Kakfa logs using sl4j, but if you add the dependency slf4j-log4j12, it will log using log4j on himself, you should not forgot to add something like:

    log4j.logger.org.apache.kafka=LEVEL,otherAppender

##ZMQAppender

A ØMQ (ZeroMQ, http://zeromq.org) Appender for log4j, that serialize Log4JEvent using the java serialization format.

ZeroMQ are super-magic socket that totally hides the danger and complexity of raw TCP socket. It reduces the message loses, and prevent 
the application hang because of slow log server, unlike the commonly used Socket Appender.

To install it, just run `mvn package` and add `target/zmqappender.jar` to your project's classpath. The default configuration of method and type should fit comme usage where logs are send to a remote central server, so only endpoint should be changed.


 * The options tied to ØMQ are
    * endoint: the endpoint URL, like `tcp://localhost:2120`, mandatory.
    * type: the socket type, either `PUB` or `PUSH`, default to `PUB`.
    * method: the socket connection method, either `connect` or `bind`, default to `connect`.
    * hwm: the HWM for the socket, default to 1000.

A complete declaration is :

    log4j.appender.A1=loghub.log4j.ZMQAppender
    log4j.appender.A1.endpoint=tcp://localhost:2120
    log4j.appender.A1.method=connect
    log4j.appender.A1.type=pub
    log4j.appender.A1.hwm=1000
    log4j.appender.A1.hostname=myhost
    log4j.appender.A1.locationInfo=true
    log4j.appender.A1.serializer=loghub.log4j.MsgPackSerializer
    log4j.appender.A1.application=some_application_name
    log4j.rootLogger=TRACE, A1
