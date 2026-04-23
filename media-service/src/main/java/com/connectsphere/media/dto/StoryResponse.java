package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import java.time.Instant;

public record StoryResponse(
        Long storyId,
        Long authorId,
        String mediaUrl,
        String caption,
        MediaType mediaType,
        long viewsCount,
        Instant expiresAt,
        Instant createdAt,
        boolean active
) {
}
