package com.connectsphere.follow.dto;

public record UserSearchResponse(
        Long userId,
        String username,
        String fullName,
        String profilePicUrl,
        String role
) {
}
