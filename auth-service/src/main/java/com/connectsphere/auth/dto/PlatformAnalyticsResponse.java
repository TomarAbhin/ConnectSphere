package com.connectsphere.auth.dto;

import java.time.Instant;
import java.util.List;

public record PlatformAnalyticsResponse(
        long totalUsers,
        long activeUsers,
        long suspendedUsers,
        long dailyActiveUsers,
        long totalPosts,
        List<TrendingHashtagResponse> trendingHashtags,
        Instant generatedAt
) {
}