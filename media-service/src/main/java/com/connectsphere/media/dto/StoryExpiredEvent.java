package com.connectsphere.media.dto;

import java.time.Instant;

public record StoryExpiredEvent(
        Long storyId,
        Long authorId,
        String mediaUrl,
        Instant expiredAt
) {
}
