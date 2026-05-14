package com.connectsphere.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter("http://auth");

    @Test
    void extractBearerTokenRemovesPrefix() {
        assertEqualsToken("abc123", ReflectionTestUtils.invokeMethod(filter, "extractBearerToken", "Bearer abc123"));
        assertNull(ReflectionTestUtils.invokeMethod(filter, "extractBearerToken", "Basic abc123"));
    }

    @Test
    void publicPathsBypassAuthentication() {
        MockServerWebExchange publicExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login").build());
        Boolean publicPath = ReflectionTestUtils.invokeMethod(filter, "isPublicPath", publicExchange);
        assertTrue(Boolean.TRUE.equals(publicPath));

        MockServerWebExchange protectedExchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/posts/1").build());
        Boolean protectedPath = ReflectionTestUtils.invokeMethod(filter, "isPublicPath", protectedExchange);
        assertFalse(Boolean.TRUE.equals(protectedPath));
    }

    private static void assertEqualsToken(String expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}