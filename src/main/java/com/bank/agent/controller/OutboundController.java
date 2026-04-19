package com.bank.agent.controller;

import com.bank.agent.model.OutboundRequest;
import com.bank.agent.service.OutboundMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint for the outbound flow.
 * Pega Launchpad POSTs here → agent forwards to bank IBM MQ.
 *
 * Secured via X-API-Key header (see SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundMessageService outboundService;

    /**
     * POST /api/v1/outbound
     *
     * Headers required:
     *   X-API-Key: <configured api key>
     *   Content-Type: application/json
     *
     * Returns 200 OK only after the message is successfully placed on IBM MQ.
     */
    @PostMapping("/outbound")
    public ResponseEntity<Map<String, String>> receiveOutbound(
            @RequestBody @Valid OutboundRequest request) {

        log.info("Outbound request received: correlationId={}, type={}",
                request.getCorrelationId(), request.getMessageType());

        outboundService.forwardToMq(request);

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "correlationId", request.getCorrelationId()
        ));
    }
}
