package com.connectsphere.comment.dto;

import java.time.Instant;

public record CommentResponse(
        Long commentId,
        Long postId,
        Long authorId,
        Long parentCommentId,
        String content,
        long likesCount,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
}
