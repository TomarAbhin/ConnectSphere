package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.ContentReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateContentReportStatusRequest(
        @NotNull(message = "Status is required")
        ContentReportStatus status,
        @Size(max = 2000, message = "Admin notes must be up to 2000 characters")
        String adminNotes
) {
}