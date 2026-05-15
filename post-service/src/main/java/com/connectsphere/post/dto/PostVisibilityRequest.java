package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostVisibility;
import jakarta.validation.constraints.NotNull;

public record PostVisibilityRequest(
        @NotNull(message = "Visibility is required")
        PostVisibility visibility
) {
}
