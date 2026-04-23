package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStoryRequest(
        @NotBlank(message = "Media URL is required")
        @Size(max = 1000, message = "Media URL must be up to 1000 characters")
        String mediaUrl,

        @Size(max = 1000, message = "Caption must be up to 1000 characters")
        String caption,

        @NotNull(message = "Media type is required")
        MediaType mediaType
) {
}
