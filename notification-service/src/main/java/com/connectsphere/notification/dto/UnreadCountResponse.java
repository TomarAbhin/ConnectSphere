package com.connectsphere.notification.dto;

public record UnreadCountResponse(
        Long recipientId,
        long unreadCount
) {
}
