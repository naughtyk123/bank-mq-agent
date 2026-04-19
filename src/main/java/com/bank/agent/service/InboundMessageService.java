package com.bank.agent.service;

import com.bank.agent.client.PegaDxApiClient;
import com.bank.agent.exception.PegaApiException;
import com.bank.agent.mapper.MessageMapper;
import com.bank.agent.model.MessageType;
import com.bank.agent.model.PegaRequest;
import com.bank.agent.model.PegaResponse;
import com.bank.agent.producer.MqOutboundProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the inbound flow for all three message types (MT, MX, FED):
 *   Bank MQ → parse (type-specific) → Pega DX API → ACK
 *
 * Retry: 3 attempts, exponential backoff starting at 2s.
 * Recovery: forwards to dead-letter queue after all retries exhausted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundMessageService {

    private final PegaDxApiClient pegaClient;
    private final MessageMapper messageMapper;
    private final MqOutboundProducer mqProducer;

    @Value("${agent.mq.dead-letter-queue}")
    private String deadLetterQueue;

    @Retryable(
            retryFor = {PegaApiException.class, RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void process(String rawMessage, String messageId, MessageType type) {
        log.debug("Processing {} message: messageId={}", type, messageId);

        // PegaRequest request = messageMapper.toPegaRequest(rawMessage, type);
         // 🔹 Build request body
        Map<String, Object> request = new HashMap<>();

        request.put("caseTypeID", "MQMessage");

        Map<String, Object> content = new HashMap<>();
        content.put("MQMessage", rawMessage);
        content.put("MQMessageType", type);
        content.put("Indicator", "Inbound");

        request.put("content", content);
        request.put("processID", "pyStartCase");
        PegaResponse response = pegaClient.createCase(request);

        log.info("Inbound processed: type={}, messageId={}, pegaCaseId={}, pegaStatus={}",
                type, messageId, response.getID(), response.getStatus());
    }

    @Recover
    public void recover(Exception ex, String rawMessage, String messageId, MessageType type) {
        log.error("All retries exhausted for {} messageId={}. Sending to DLQ.", type, messageId, ex);
        try {
            mqProducer.sendToDlq(rawMessage, messageId, ex.getMessage(), deadLetterQueue);
            log.warn("Message forwarded to DLQ: type={}, messageId={}", type, messageId);
        } catch (Exception dlqEx) {
            log.error("CRITICAL: Failed to forward to DLQ: type={}, messageId={}", type, messageId, dlqEx);
            throw new RuntimeException("DLQ send failed for messageId=" + messageId, dlqEx);
        }
    }
}
