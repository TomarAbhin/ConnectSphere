package com.connectsphere.search.dto;

import com.connectsphere.search.entity.PostVisibility;
import java.time.Instant;
import java.util.List;

public record PostSearchResponse(
        Long postId,
        Long authorId,
        String content,
        List<String> hashtags,
        PostVisibility visibility,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted
) {
}