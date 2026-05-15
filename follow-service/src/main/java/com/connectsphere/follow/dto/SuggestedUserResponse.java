package com.connectsphere.follow.dto;

public record SuggestedUserResponse(
        Long userId,
        String username,
        String fullName,
        String profilePicUrl,
        String role,
        long mutualConnectionCount
) {
}
