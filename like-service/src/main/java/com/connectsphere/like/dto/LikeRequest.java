package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import jakarta.validation.constraints.NotNull;

public record LikeRequest(
        @NotNull(message = "Target type is required")
        TargetType targetType,

        @NotNull(message = "Target id is required")
        Long targetId,

        @NotNull(message = "Reaction type is required")
        ReactionType reactionType
) {
}
