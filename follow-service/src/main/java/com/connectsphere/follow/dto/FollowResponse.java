package com.connectsphere.follow.dto;

import com.connectsphere.follow.entity.FollowStatus;
import java.time.Instant;

public record FollowResponse(
        Long followId,
        Long followerId,
        Long followedId,
        FollowStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
