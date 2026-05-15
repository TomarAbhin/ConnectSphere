package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.PostVisibility;
import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long postId,
        Long authorId,
        String authorUsername,
        String authorFullName,
        String authorProfilePicUrl,
        String content,
        List<String> mediaUrls,
        PostType postType,
        PostVisibility visibility,
        long likesCount,
        long commentsCount,
        long sharesCount,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted
) {
}
