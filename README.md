ZMQAppender
===========

A [Apache Kafka](https://kafka.apache.org) Appender for log4j.

It serialize the Log4j events in native java format, to avoid inefficient common formats like json and allow to keep all the events data as is. It can also uses [msgpack](https://msgpack.org).

To install it, just run `mvn package` and add target/kafkalog4jappender.jar to your project's classpath. The default configuration of method and type should fit common usage where logs are send to kafka cluser, so only brokers should be changed.

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
 * common options are
   * hostname: it will be added as a `hostname` property, default to the value of `java.net.InetAddress.getLocalHost().getHostName()`.
   * locationInfo: true of false, it will send or not the log event location (file, line, method), default to false.
   * application: the application name, it's optionnal.
 * serializer: a class used to serialize Log4j's events to a byte array. It must implements loghub.log4j.Serializer class and provide a constructor with no arguments.

Two serializers are provided: loghub.log4j.JavaSerializer (using native java serialization) and loghub.log4j.MsgPackSerializer (serialized to a msgpack object, as a map)

A complete declaration is :

    log4j.appender.A1=loghub.log4j.KafkaAppender
    log4j.appender.A1=loghub.log4j.KafkaAppender
    log4j.appender.A1.brokerList=127.0.0.1:9093
    log4j.appender.A1.topic=test-topic
    log4j.appender.A1.requiredNumAcks=1
    log4j.appender.A1.syncSend=false
    log4j.appender.A1.hostname=myhost
    log4j.appender.A1.locationInfo=true
    log4j.appender.A1.serializer=loghub.log4j.JavaSerializer
    log4j.appender.A1.application=some_application_name
    log4j.rootLogger=TRACE, A1
