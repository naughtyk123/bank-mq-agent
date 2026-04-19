package com.bank.agent.producer;

import jakarta.jms.TextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends messages to IBM MQ queues.
 * Used by both the outbound flow (Pega → MQ) and DLQ forwarding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqOutboundProducer {

    private final JmsTemplate jmsTemplate;

    /**
     * Sends a message to the specified queue with a JMSCorrelationID.
     */
    public void send(String payload, String correlationId, String queue) {
        log.debug("Sending to MQ queue={}, correlationId={}", queue, correlationId);
        jmsTemplate.send(queue, session -> {
            TextMessage msg = session.createTextMessage(payload);
            msg.setJMSCorrelationID(correlationId);
            return msg;
        });
        log.debug("Message sent: queue={}, correlationId={}", queue, correlationId);
    }

    /**
     * Sends a failed message to the dead-letter queue with error metadata.
     */
    public void sendToDlq(String originalPayload, String messageId,
                          String errorReason, String dlqName) {
        jmsTemplate.send(dlqName, session -> {
            TextMessage msg = session.createTextMessage(originalPayload);
            msg.setJMSCorrelationID(messageId);
            msg.setStringProperty("DLQ_ERROR_REASON", errorReason);
            msg.setStringProperty("DLQ_ORIGINAL_MESSAGE_ID", messageId);
            msg.setLongProperty("DLQ_TIMESTAMP", System.currentTimeMillis());
            return msg;
        });
    }
}
