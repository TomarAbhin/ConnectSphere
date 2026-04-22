package com.connectsphere.notification.dto;

public record UserResponse(
        Long userId,
        String username,
        String email,
        String fullName,
        String bio,
        String profilePicUrl,
        String role,
        String provider,
        boolean active
) {
}
