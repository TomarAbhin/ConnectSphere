package com.connectsphere.notification.resource;

import com.connectsphere.notification.dto.BulkNotificationRequest;
import com.connectsphere.notification.dto.CreateNotificationRequest;
import com.connectsphere.notification.dto.NotificationResponse;
import com.connectsphere.notification.dto.SendEmailRequest;
import com.connectsphere.notification.dto.UnreadCountResponse;
import com.connectsphere.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationResource {

    private final NotificationService notificationService;

    public NotificationResource(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateNotificationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(authorization, request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<NotificationResponse>> sendBulkNotification(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody BulkNotificationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.sendBulkNotification(authorization, request));
    }

    @PostMapping("/email")
    public ResponseEntity<NotificationResponse> sendEmailAlert(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody SendEmailRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.sendEmailAlert(authorization, request));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ResponseEntity.ok(notificationService.getAll(authorization));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> getById(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(notificationService.getNotificationById(authorization, notificationId));
    }

    @GetMapping("/recipient/{recipientId}")
    public ResponseEntity<List<NotificationResponse>> getByRecipient(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long recipientId
    ) {
        return ResponseEntity.ok(notificationService.getByRecipient(authorization, recipientId));
    }

    @GetMapping("/recipient/{recipientId}/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long recipientId
    ) {
        return ResponseEntity.ok(notificationService.getUnreadCount(authorization, recipientId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(authorization, notificationId));
    }

    @PutMapping("/recipient/{recipientId}/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long recipientId
    ) {
        notificationService.markAllRead(authorization, recipientId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long notificationId
    ) {
        notificationService.deleteNotification(authorization, notificationId);
        return ResponseEntity.noContent().build();
    }
}
