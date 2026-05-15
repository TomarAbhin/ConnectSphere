package com.connectsphere.like.dto;

import com.connectsphere.like.entity.TargetType;

public record LikeCountResponse(
        TargetType targetType,
        Long targetId,
        long count
) {
}
