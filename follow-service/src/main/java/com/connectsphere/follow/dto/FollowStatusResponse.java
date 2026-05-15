package com.connectsphere.follow.dto;

import com.connectsphere.follow.entity.FollowStatus;

public record FollowStatusResponse(
        boolean following,
        Long followId,
        FollowStatus status
) {
}
