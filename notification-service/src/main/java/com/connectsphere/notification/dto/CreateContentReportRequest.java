package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.ContentReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateContentReportRequest(
        @NotNull(message = "Target type is required")
        ContentReportTargetType targetType,
        @NotNull(message = "Target id is required")
        Long targetId,
        Long targetUserId,
        @NotBlank(message = "Reason is required")
        @Size(max = 2000, message = "Reason must be up to 2000 characters")
        String reason
) {
}