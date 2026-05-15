package com.connectsphere.search.dto;

import java.time.Instant;

public record HashtagResponse(
        Long hashtagId,
        String tag,
        long postCount,
        Instant lastUsedAt,
        Instant createdAt
) {
}