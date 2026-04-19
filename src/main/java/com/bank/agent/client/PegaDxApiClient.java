package com.bank.agent.client;

import com.bank.agent.exception.PegaApiException;
import com.bank.agent.model.PegaRequest;
import com.bank.agent.model.PegaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * HTTP client for the Pega Launchpad DX API.
 *
 * Inbound flow : createCase() — POST /cases
 * Outbound flow : not needed (Pega calls the agent's REST endpoint instead)
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class PegaDxApiClient {
        @Value("${agent.pega.base-url}")
        private String baseUrl;
        private final WebClient pegaWebClient;
        private final OAuth2TokenService tokenService;

        /**
         * Creates a case in Pega Launchpad.
         * Blocks until a response is received (acceptable in a JMS listener thread).
         *
         * @throws PegaApiException on any non-2xx response
         */
        public PegaResponse createCase(Map<String, Object> request) {
                log.debug("Calling Pega DX API createCase, caseTypeID={}", request.get("caseTypeID"));

                String token = tokenService.getToken().block();

                return pegaWebClient.post()
                                .uri(baseUrl)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .retrieve()
                                .onStatus(HttpStatusCode::is4xxClientError, response -> response
                                                .bodyToMono(String.class)
                                                .flatMap(body -> Mono.error(
                                                                new PegaApiException("Pega client error "
                                                                                + response.statusCode() + ": "
                                                                                + body))))
                                .onStatus(HttpStatusCode::is5xxServerError, response -> response
                                                .bodyToMono(String.class)
                                                .flatMap(body -> Mono.error(
                                                                new PegaApiException("Pega server error "
                                                                                + response.statusCode() + ": "
                                                                                + body))))
                                .bodyToMono(PegaResponse.class)
                                .doOnNext(r -> log.info("Pega case created: ID={}, status={}", r.getID(),
                                                r.getStatus()))
                                .block();
        }
}
