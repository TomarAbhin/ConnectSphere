package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotNull(message = "Recipient id is required")
        Long recipientId,
        Long actorId,
        @NotNull(message = "Action type is required")
        NotificationType actionType,
        @NotNull(message = "Target type is required")
        NotificationTargetType targetType,
        Long targetId,
        @NotBlank(message = "Message is required")
        @Size(max = 1000, message = "Message must be up to 1000 characters")
        String message
) {
}
