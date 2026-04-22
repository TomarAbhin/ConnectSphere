package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.BulkNotificationRequest;
import com.connectsphere.notification.dto.CreateNotificationRequest;
import com.connectsphere.notification.dto.NotificationResponse;
import com.connectsphere.notification.dto.SendEmailRequest;
import com.connectsphere.notification.dto.UnreadCountResponse;
import java.util.List;

public interface NotificationService {

    NotificationResponse createNotification(String authorizationHeader, CreateNotificationRequest request);

    List<NotificationResponse> sendBulkNotification(String authorizationHeader, BulkNotificationRequest request);

    NotificationResponse sendEmailAlert(String authorizationHeader, SendEmailRequest request);

    NotificationResponse getNotificationById(String authorizationHeader, Long notificationId);

    List<NotificationResponse> getByRecipient(String authorizationHeader, Long recipientId);

    UnreadCountResponse getUnreadCount(String authorizationHeader, Long recipientId);

    NotificationResponse markAsRead(String authorizationHeader, Long notificationId);

    void markAllRead(String authorizationHeader, Long recipientId);

    void deleteNotification(String authorizationHeader, Long notificationId);

    List<NotificationResponse> getAll(String authorizationHeader);
}
