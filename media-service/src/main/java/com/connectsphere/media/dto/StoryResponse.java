package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import java.time.Instant;

public record StoryResponse(
        Long storyId,
        Long authorId,
        String authorUsername,
        String authorFullName,
        String authorProfilePicUrl,
        String mediaUrl,
        String caption,
        MediaType mediaType,
        long viewsCount,
        long likesCount,
        Instant expiresAt,
        Instant createdAt,
        boolean active
) {
}
