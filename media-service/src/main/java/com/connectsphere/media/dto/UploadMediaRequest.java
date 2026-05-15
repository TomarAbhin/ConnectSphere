package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import jakarta.validation.constraints.NotNull;

public record UploadMediaRequest(
        Long linkedPostId,
        @NotNull(message = "Media type is required")
        MediaType mediaType
) {
}
