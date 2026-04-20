package com.connectsphere.auth.dto;

import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.UserRole;
import java.time.Instant;

public record UserResponse(
        Long userId,
        String username,
        String email,
        String fullName,
        String bio,
        String profilePicUrl,
        UserRole role,
        AuthProvider provider,
        boolean active,
        Instant createdAt
) {
}
