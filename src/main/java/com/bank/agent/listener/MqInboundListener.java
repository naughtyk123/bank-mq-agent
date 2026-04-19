package com.bank.agent.listener;

import com.bank.agent.model.MessageType;
import com.bank.agent.service.InboundMessageService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Listens to three bank IBM MQ queues:
 *   - MT  : SWIFT MT messages     (mtListenerFactory  — high concurrency)
 *   - MX  : ISO 20022 XML         (mxListenerFactory  — medium concurrency)
 *   - FED : Fedwire messages       (fedListenerFactory — low concurrency)
 *
 * ACK strategy: CLIENT_ACKNOWLEDGE
 *   message.acknowledge() called ONLY after successful Pega DX API response.
 *   session.recover() on failure returns the message to queue for retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqInboundListener {

    private final InboundMessageService inboundService;

    // ── MT ──────────────────────────────────────────────────────────────────

    @JmsListener(
            destination = "${agent.mq.mt-queue}",
            containerFactory = "mtListenerFactory"
    )
    public void onMtMessage(Message message, Session session) throws JMSException {
        handle(message, session, MessageType.MT);
    }

    // // ── MX ──────────────────────────────────────────────────────────────────

    // @JmsListener(
    //         destination = "${agent.mq.mx-queue}",
    //         containerFactory = "mxListenerFactory"
    // )
    // public void onMxMessage(Message message, Session session) throws JMSException {
    //     handle(message, session, MessageType.MX);
    // }

    // // ── FED ─────────────────────────────────────────────────────────────────

    // @JmsListener(
    //         destination = "${agent.mq.fed-queue}",
    //         containerFactory = "fedListenerFactory"
    // )
    // public void onFedMessage(Message message, Session session) throws JMSException {
    //     handle(message, session, MessageType.FED);
    // }

    // ── shared handler ───────────────────────────────────────────────────────

    private void handle(Message message, Session session, MessageType type) throws JMSException {
        String messageId = message.getJMSMessageID();
        String correlationId = message.getJMSCorrelationID();

        log.info("Received {} message: messageId={}, correlationId={}", type, messageId, correlationId);

        if (!(message instanceof TextMessage textMessage)) {
            log.error("Unsupported message type for {}: {}", type, message.getClass().getSimpleName());
            message.acknowledge();
            return;
        }

        String body = textMessage.getText();
        log.debug("{} message body: {}", type, body);

        try {
            inboundService.process(body, messageId, type);
            message.acknowledge();
            log.info("{} message acknowledged: messageId={}", type, messageId);
        } catch (Exception ex) {
            log.error("Failed to process {} message: messageId={}, error={}",
                    type, messageId, ex.getMessage(), ex);
            session.recover();
        }
    }
}
