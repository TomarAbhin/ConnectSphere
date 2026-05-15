package com.connectsphere.auth.dto;

import com.connectsphere.auth.entity.UserRole;

public record UserSearchResponse(
        Long userId,
        String username,
        String fullName,
        String profilePicUrl,
        UserRole role
) {
}
