package com.connectsphere.follow.dto;

public record FollowCountResponse(
        Long userId,
        long followerCount,
        long followingCount,
        long mutualCount
) {
}
