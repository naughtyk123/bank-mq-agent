package com.bank.agent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${agent.security.api-key}")
    private String expectedApiKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    private OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();

                // Skip actuator
                if (path.startsWith("/actuator")) {
                    chain.doFilter(request, response);
                    return;
                }

                String providedKey = request.getHeader(API_KEY_HEADER);

                if (providedKey != null && expectedApiKey.trim().equals(providedKey.trim())) {

                    // ✅ THIS IS THE FIX → mark request as authenticated
                    var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "api-user",
                            null,
                            java.util.Collections.emptyList());

                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .setAuthentication(auth);

                    chain.doFilter(request, response);

                } else {
                    log.warn("Rejected request to {} — invalid API key. Expected={}, Received={}",
                            path, expectedApiKey, providedKey);

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                }
            }
        };
    }
}
