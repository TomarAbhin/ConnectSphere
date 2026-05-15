package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must be up to 5000 characters")
        String content,

        List<@Size(max = 1000, message = "Media URL must be up to 1000 characters") String> mediaUrls,

        PostType postType,

        PostVisibility visibility
) {
}
