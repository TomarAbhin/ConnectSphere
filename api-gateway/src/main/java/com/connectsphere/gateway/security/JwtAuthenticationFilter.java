package com.connectsphere.gateway.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/oauth2/",
            "/api/v1/auth/users/",
            "/api/v1/search/",
            "/api/v1/hashtags/",
            "/api/v1/likes/summary/",
            "/api/v1/media/files/",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html"
    );

        private static final Pattern PUBLIC_POST_DETAIL_PATTERN = Pattern.compile("^/api/v1/posts/\\d+$");

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
            .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
            .flatMap(response -> Boolean.TRUE.equals(response == null ? null : response.get("valid")) ? chain.filter(exchange) : unauthorized(exchange, "Invalid or expired token"))
                .onErrorResume(ex -> unauthorized(exchange, "Unable to validate token"));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (method != HttpMethod.GET) {
            return false;
        }
        return "/api/v1/posts/feed".equals(path)
                || path.startsWith("/api/v1/posts/user/")
                || "/api/v1/posts/search".equals(path)
                || PUBLIC_POST_DETAIL_PATTERN.matcher(path).matches();
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

}