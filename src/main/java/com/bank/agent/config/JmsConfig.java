package com.bank.agent.config;

import com.ibm.mq.jakarta.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

@Slf4j
@Configuration
@EnableJms
public class JmsConfig {

    // --- MQ Connection (matches your YAML) ---
    @Value("${agent.mq.host}")
    private String mqHost;

    @Value("${agent.mq.port}")
    private int mqPort;

    @Value("${agent.mq.queue-manager}")
    private String queueManager;

    @Value("${agent.mq.channel}")
    private String channel;

    @Value("${agent.mq.user}")
    private String mqUser;

    @Value("${agent.mq.password}")
    private String mqPassword;

    // --- SSL ---
    @Value("${agent.mq.ssl.cipher-suite}")
    private String sslCipherSuite;

    // --- Concurrency ---
    @Value("${agent.mq.mt-concurrency:2-10}")
    private String mtConcurrency;

    @Value("${agent.mq.mx-concurrency:1-5}")
    private String mxConcurrency;

    @Value("${agent.mq.fed-concurrency:1-3}")
    private String fedConcurrency;

    // -----------------------------------------------------------------------
    // ✅ ConnectionFactory (CLEAN VERSION)
    // -----------------------------------------------------------------------
    @Bean
    public ConnectionFactory mqConnectionFactory() throws Exception {

        MQQueueConnectionFactory factory = new MQQueueConnectionFactory();

        factory.setHostName(mqHost);
        factory.setPort(mqPort);
        factory.setQueueManager(queueManager);
        factory.setChannel(channel);
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);

        // ✅ Required for IBM MQ Cloud
        factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);

        factory.setStringProperty(WMQConstants.USERID, mqUser);
        factory.setStringProperty(WMQConstants.PASSWORD, mqPassword);

        // ✅ SSL Cipher (must match MQ server)
        factory.setSSLCipherSuite(sslCipherSuite);

        log.info("MQ Connected → {}:{} QM={} Channel={}",
                mqHost, mqPort, queueManager, channel);

        return factory;
    }

    // -----------------------------------------------------------------------
    // Listener Factories
    // -----------------------------------------------------------------------

    @Bean
    public DefaultJmsListenerContainerFactory mtListenerFactory(ConnectionFactory cf) {
        return buildFactory(cf, mtConcurrency);
    }

    @Bean
    public DefaultJmsListenerContainerFactory mxListenerFactory(ConnectionFactory cf) {
        return buildFactory(cf, mxConcurrency);
    }

    @Bean
    public DefaultJmsListenerContainerFactory fedListenerFactory(ConnectionFactory cf) {
        return buildFactory(cf, fedConcurrency);
    }

    // -----------------------------------------------------------------------
    // JmsTemplate
    // -----------------------------------------------------------------------

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory cf) {
        JmsTemplate template = new JmsTemplate(cf);
        template.setSessionTransacted(true);
        return template;
    }

    // -----------------------------------------------------------------------
    // Common Config
    // -----------------------------------------------------------------------

    private DefaultJmsListenerContainerFactory buildFactory(ConnectionFactory cf, String concurrency) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();

        factory.setConnectionFactory(cf);

        // ✅ Recommended (auto rollback on failure)
        factory.setSessionTransacted(true);

        factory.setConcurrency(concurrency);
        factory.setRecoveryInterval(5000L);

        factory.setErrorHandler(t -> log.error("JMS Listener error", t));

        return factory;
    }
}