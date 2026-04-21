package com.connectsphere.follow.dto;

public record FollowerResponse(
        Long userId,
        String username,
        String fullName,
        String profilePicUrl,
        String role
) {
}
