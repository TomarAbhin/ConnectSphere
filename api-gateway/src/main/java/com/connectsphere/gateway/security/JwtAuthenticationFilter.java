package com.connectsphere.gateway.security;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth/",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html"
    );

    private final WebClient webClient;

    public JwtAuthenticationFilter(@Value("${app.services.auth.url}") String authServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(authServiceUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicPath(exchange)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractBearerToken(authorization);
        if (token == null) {
            return unauthorized(exchange, "Authorization header is required");
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/auth/validate").queryParam("token", token).build())
                .retrieve()
                .bodyToMono(ValidateResponse.class)
                .timeout(Duration.ofSeconds(3))
                .flatMap(response -> response != null && response.valid() ? chain.filter(exchange) : unauthorized(exchange, "Invalid or expired token"))
                .onErrorResume(ex -> unauthorized(exchange, "Unable to validate token"));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7).trim();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}").getBytes();
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private record ValidateResponse(boolean valid) {
    }
}