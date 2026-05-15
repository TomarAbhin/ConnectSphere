package com.connectsphere.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotNull(message = "Post id is required")
        Long postId,

        Long parentCommentId,

        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must be up to 5000 characters")
        String content
) {
}
