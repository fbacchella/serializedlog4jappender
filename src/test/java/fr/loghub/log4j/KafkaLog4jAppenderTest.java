/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.loghub.log4j;

import org.apache.kafka.common.config.ConfigException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Assert;
import org.junit.Test;

import fr.loghub.log4j.KafkaAppender;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class KafkaLog4jAppenderTest {

    Logger logger = Logger.getLogger(KafkaLog4jAppenderTest.class);

    @Test(timeout=1000, expected=ConfigException.class)
    public void testKafkaLog4jConfigHostMissing() {
        // host missing
        Properties props = new Properties();
        props.put("log4j.rootLogger", "INFO");
        props.put("log4j.appender.KAFKA", fr.loghub.log4j.MockKafkaLog4jAppender.class.getName());
        props.put("log4j.appender.KAFKA.Topic", "test-topic");
        props.put("log4j.logger.kafka.log4j", "INFO, KAFKA");
        PropertyConfigurator.configure(props);
    }

    @Test(timeout=1000, expected=ConfigException.class)
    public void testKafkaLog4jConfigTopicMissing() {
        // topic missing
        Properties props = new Properties();
        props.put("log4j.rootLogger", "INFO");
        props.put("log4j.appender.KAFKA", fr.loghub.log4j.MockKafkaLog4jAppender.class.getName());
        props.put("log4j.appender.KAFKA.brokerList", "127.0.0.1:9093");
        props.put("log4j.logger.kafka.log4j", "INFO, KAFKA");
        PropertyConfigurator.configure(props);
    }

    @Test(timeout=1000)
    public void testLog4jAppends() throws UnsupportedEncodingException {
        PropertyConfigurator.configure(getLog4jConfig());

        for (int i = 1; i <= 5; ++i) {
            logger.error(getMessage(i));
        }

        Assert.assertEquals(
                5, ((MockKafkaLog4jAppender) (Logger.getRootLogger().getAppender("KAFKA"))).getHistory().size());
    }

    @Test
    public void testJaasParsing() throws URISyntaxException {
        URL kUrl = this.getClass().getClassLoader().getResource("kafka_jaas.conf");
        URI kUri = new URI(kUrl + "#KafkaServer");
        PropertyConfigurator.configure(getLog4jConfig(Collections.singletonMap("log4j.appender.KAFKA.clientJaasConfUri", kUri.toString())));
        KafkaAppender app = (KafkaAppender) (Logger.getRootLogger().getAppender("KAFKA"));
        String parsedJaasLine = app.readJaasConfig(app.getClientJaasConfUri());
        Assert.assertTrue(parsedJaasLine.contains("com.sun.security.auth.module.Krb5LoginModule required "));
        Assert.assertTrue(parsedJaasLine.contains(" keyTab=\"/etc/security/keytabs/kafka_server.keytab\""));
        Assert.assertTrue(parsedJaasLine.contains("org.apache.kafka.common.security.plain.PlainLoginModule optional "));
        Assert.assertTrue(parsedJaasLine.contains(" username=\"admin\""));
    }

    private byte[] getMessage(int i) throws UnsupportedEncodingException {
        return ("test_" + i).getBytes("UTF-8");
    }

    private Properties getLog4jConfig() {
        Map<String, String> otherProps = Collections.emptyMap();
        return getLog4jConfig(otherProps);
    }

    private Properties getLog4jConfig(Map<String, String> otherProps) {
        Properties props = new Properties();
        props.put("log4j.rootLogger", "INFO, KAFKA");
        props.put("log4j.appender.KAFKA", fr.loghub.log4j.MockKafkaLog4jAppender.class.getName());
        props.put("log4j.appender.KAFKA.BrokerList", "127.0.0.1:9093");
        props.put("log4j.appender.KAFKA.Topic", "test-topic");
        props.put("log4j.appender.KAFKA.RequiredNumAcks", "1");
        props.put("log4j.appender.KAFKA.SyncSend", "false");
        props.put("log4j.logger.kafka.log4j", "INFO, KAFKA");
        props.putAll(otherProps);
        return props;
    }
}

