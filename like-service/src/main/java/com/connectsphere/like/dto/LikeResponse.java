package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import java.time.Instant;

public record LikeResponse(
        Long likeId,
        Long userId,
        Long targetId,
        TargetType targetType,
        ReactionType reactionType,
        Instant createdAt,
        Instant updatedAt
) {
}
