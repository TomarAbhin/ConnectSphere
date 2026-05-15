package com.connectsphere.search.dto;

import com.connectsphere.search.entity.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IndexPostRequest(
        @NotNull Long postId,
        @NotNull Long authorId,
        @NotBlank @Size(max = 5000) String content,
        List<String> hashtags,
        PostVisibility visibility,
        Boolean deleted
) {
}