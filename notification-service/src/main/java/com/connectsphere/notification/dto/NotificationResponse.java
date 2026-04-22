package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        Long recipientId,
        Long actorId,
        NotificationType actionType,
        NotificationTargetType targetType,
        Long targetId,
        String message,
        boolean read,
        Instant readAt,
        Instant createdAt,
        Instant updatedAt
) {
}
