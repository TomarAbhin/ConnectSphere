package com.connectsphere.like.dto;

import java.time.Instant;

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
        Instant createdAt
) {
}
