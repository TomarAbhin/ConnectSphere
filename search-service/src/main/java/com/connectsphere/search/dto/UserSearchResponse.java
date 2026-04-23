package com.connectsphere.search.dto;

public record UserSearchResponse(
        Long userId,
        String username,
        String fullName,
        String profilePicUrl,
        UserRole role
) {
}