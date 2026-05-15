package com.connectsphere.follow.dto;

public record FollowingResponse(
        Long userId,
        String username,
        String fullName,
        String profilePicUrl,
        String role
) {
}
