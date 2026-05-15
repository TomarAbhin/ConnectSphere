package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.ContentReportStatus;
import com.connectsphere.notification.entity.ContentReportTargetType;
import java.time.Instant;

public record ContentReportResponse(
        Long reportId,
        Long reporterId,
        ContentReportTargetType targetType,
        Long targetId,
        Long targetUserId,
        String reason,
        ContentReportStatus status,
        String adminNotes,
        Long reviewedById,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}