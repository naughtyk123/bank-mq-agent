package com.bank.agent.service;

import com.bank.agent.mapper.MessageMapper;
import com.bank.agent.model.OutboundRequest;
import com.bank.agent.producer.MqOutboundProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the outbound flow:
 *   Pega REST POST → serialize → Bank MQ
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundMessageService {

    private final MqOutboundProducer mqProducer;
    private final MessageMapper messageMapper;

    @Value("${agent.mq.outbound-queue}")
    private String defaultOutboundQueue;

    public void forwardToMq(OutboundRequest request) {
        log.debug("Forwarding outbound message to MQ: correlationId={}, type={}",
                request.getCorrelationId(), request.getMessageType());

        String mqPayload = messageMapper.toMqPayload(request);

        // Use override queue if provided, otherwise default
        String targetQueue = (request.getTargetQueue() != null && !request.getTargetQueue().isBlank())
                ? request.getTargetQueue()
                : defaultOutboundQueue;

        mqProducer.send(mqPayload, request.getCorrelationId(), targetQueue);

        log.info("Outbound message sent to MQ: correlationId={}, queue={}",
                request.getCorrelationId(), targetQueue);
    }
}
