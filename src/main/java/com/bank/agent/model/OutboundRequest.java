package com.bank.agent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload Pega Launchpad POSTs to the agent's outbound REST endpoint.
 * Adjust fields to match your bank's MQ message contract.
 */
@Data
public class OutboundRequest {

    @NotBlank(message = "correlationId is required")
    private String correlationId;

    @NotBlank(message = "messageType is required")
    private String messageType;

    /** Raw payload to be forwarded as-is to the bank MQ */
    private String payload;

    /** Optional: target queue override (defaults to agent.mq.outbound-queue) */
    private String targetQueue;
}
