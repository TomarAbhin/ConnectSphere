package com.connectsphere.auth.dto;

import java.time.Instant;

public record TrendingHashtagResponse(
        Long hashtagId,
        String tag,
        long postCount,
        Instant lastUsedAt,
        Instant createdAt
) {
}