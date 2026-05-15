package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;

public record HasLikedResponse(
        boolean liked,
        ReactionType reactionType
) {
}
