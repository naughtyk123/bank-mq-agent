package com.bank.agent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches an OAuth2 client_credentials token from Pega and caches it until expiry.
 * Thread-safe via AtomicReference.
 */
@Slf4j
@Service
public class OAuth2TokenService {

    @Value("${agent.pega.token-url}")
    private String tokenUrl;

    @Value("${agent.pega.client-id}")
    private String clientId;

    @Value("${agent.pega.client-secret}")
    private String clientSecret;

    private final WebClient tokenClient = WebClient.create();

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public Mono<String> getToken() {
        CachedToken current = cachedToken.get();
        if (current != null && current.isValid()) {
            return Mono.just(current.accessToken);
        }
        return fetchNewToken();
    }

    private Mono<String> fetchNewToken() {
        log.debug("Fetching new Pega OAuth2 token");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        return tokenClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(resp -> {
                    CachedToken token = new CachedToken(
                            resp.accessToken,
                            Instant.now().plusSeconds(resp.expiresIn - 30) // 30s buffer
                    );
                    cachedToken.set(token);
                    log.debug("Pega OAuth2 token refreshed, expires in {}s", resp.expiresIn);
                    return resp.accessToken;
                })
                .doOnError(e -> log.error("Failed to fetch Pega OAuth2 token", e));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private long expiresIn;
    }

    static class CachedToken {
        final String accessToken;
        final Instant expiresAt;

        CachedToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
