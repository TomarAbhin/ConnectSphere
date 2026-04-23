package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import java.time.Instant;

public record MediaUploadResponse(
        Long mediaId,
        Long uploadedBy,
        String url,
        Long sizeKb,
        MediaType mediaType,
        Long linkedPostId,
        Instant uploadedAt,
        boolean deleted
) {
}
