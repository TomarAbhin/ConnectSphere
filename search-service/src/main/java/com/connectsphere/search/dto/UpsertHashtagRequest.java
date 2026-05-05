package com.connectsphere.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertHashtagRequest(
        @NotBlank(message = "Tag is required")
        @Size(max = 120, message = "Tag must be up to 120 characters")
        String tag
) {
}