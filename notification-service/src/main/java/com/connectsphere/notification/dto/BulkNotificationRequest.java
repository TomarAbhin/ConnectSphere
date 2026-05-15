package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkNotificationRequest(
        @NotEmpty(message = "Recipient ids are required")
        List<Long> recipientIds,
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
