package com.connectsphere.search.dto;

public record AuthProfileResponse(
        Long userId,
        String username,
        String email,
        String fullName,
        String bio,
        String profilePicUrl,
        String role,
        String provider,
        boolean active,
        java.time.Instant createdAt
) {
}