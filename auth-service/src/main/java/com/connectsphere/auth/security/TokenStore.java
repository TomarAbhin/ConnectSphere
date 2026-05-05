package com.connectsphere.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();
    private final Map<String, RefreshTokenRecord> refreshTokens = new ConcurrentHashMap<>();

    public void blacklist(String token, Instant expiresAt) {
        if (token == null || token.isBlank() || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            return;
        }
        blacklist.put(token, expiresAt);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        Instant expiry = blacklist.get(token);
        return expiry != null && expiry.isAfter(Instant.now());
    }

    public void storeRefreshToken(String email, String refreshToken, Instant expiresAt) {
        if (email == null || refreshToken == null || expiresAt == null) {
            return;
        }
        refreshTokens.put(refreshToken, new RefreshTokenRecord(email, expiresAt));
    }

    public boolean isValidRefreshToken(String email, String refreshToken) {
        if (email == null || refreshToken == null) {
            return false;
        }
        RefreshTokenRecord record = refreshTokens.get(refreshToken);
        return record != null
                && Objects.equals(record.email(), email)
                && record.expiresAt().isAfter(Instant.now());
    }

    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokens.remove(refreshToken);
    }

    public void revokeRefreshTokensForEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        refreshTokens.entrySet().removeIf(entry -> email.equalsIgnoreCase(entry.getValue().email()));
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredLocalTokens() {
        Instant now = Instant.now();
        blacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        refreshTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record RefreshTokenRecord(String email, Instant expiresAt) {
    }
}
