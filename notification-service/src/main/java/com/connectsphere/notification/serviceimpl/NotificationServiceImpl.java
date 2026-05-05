package com.connectsphere.notification.serviceimpl;

import com.connectsphere.notification.dto.AuthProfileResponse;
import com.connectsphere.notification.dto.BulkNotificationRequest;
import com.connectsphere.notification.dto.CreateNotificationRequest;
import com.connectsphere.notification.dto.NotificationResponse;
import com.connectsphere.notification.dto.SendEmailRequest;
import com.connectsphere.notification.dto.UnreadCountResponse;
import com.connectsphere.notification.dto.UserResponse;
import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.repository.NotificationRepository;
import com.connectsphere.notification.service.NotificationService;
import jakarta.mail.MessagingException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;
    private final String authServiceUrl;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            RestTemplate restTemplate,
            JavaMailSender mailSender,
            @Value("${app.services.auth-service.url:http://localhost:8081}") String authServiceUrl
    ) {
        this.notificationRepository = notificationRepository;
        this.restTemplate = restTemplate;
        this.mailSender = mailSender;
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    public NotificationResponse createNotification(String authorizationHeader, CreateNotificationRequest request) {
        UserResponse recipient = fetchUserById(authorizationHeader, request.recipientId());
        Long actorId = request.actorId() != null ? request.actorId() : resolveCurrentProfile(authorizationHeader).userId();
        Notification notification = new Notification();
        notification.setRecipientId(recipient.userId());
        notification.setActorId(actorId);
        notification.setActionType(request.actionType());
        notification.setTargetType(request.targetType());
        notification.setTargetId(request.targetId());
        notification.setMessage(request.message().trim());
        notification.setDeepLink(request.deepLink());
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    public List<NotificationResponse> sendBulkNotification(String authorizationHeader, BulkNotificationRequest request) {
        ensureAdmin(authorizationHeader);
        Set<Long> recipientIds = new LinkedHashSet<>(request.recipientIds());
        List<NotificationResponse> responses = new ArrayList<>();
        for (Long recipientId : recipientIds) {
            UserResponse recipient = fetchUserById(authorizationHeader, recipientId);
            NotificationResponse response = createAndSend(
                    recipient.userId(),
                    request.actorId(),
                    request.actionType(),
                    request.targetType(),
                    request.targetId(),
                    request.message()
            );
            responses.add(response);
            try {
                sendEmailToUser(recipient.email(), "ConnectSphere Alert", request.message().trim());
            } catch (RuntimeException ignored) {
            }
        }
        return responses;
    }

    @Override
    public NotificationResponse sendEmailAlert(String authorizationHeader, SendEmailRequest request) {
        UserResponse recipient = fetchUserById(authorizationHeader, request.recipientId());
        var sender = resolveCurrentProfile(authorizationHeader);
        // allow user to send email to themselves or admins to send to anyone
        if (!Objects.equals(sender.userId(), recipient.userId()) && !isAdmin(sender)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Not allowed to send email to this user");
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipient.userId());
        notification.setActorId(sender.userId());
        notification.setActionType(NotificationType.SYSTEM);
        notification.setTargetType(NotificationTargetType.SYSTEM);
        notification.setMessage(request.body().trim());
        notification.setDeepLink(request.deepLink());
        Notification saved = notificationRepository.save(notification);
        sendEmailToUser(recipient.email(), request.subject().trim(), request.body().trim());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(String authorizationHeader, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        ensureOwnerOrAdmin(authorizationHeader, notification.getRecipientId());
        return toResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByRecipient(String authorizationHeader, Long recipientId) {
        ensureOwnerOrAdmin(authorizationHeader, recipientId);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String authorizationHeader, Long recipientId) {
        ensureOwnerOrAdmin(authorizationHeader, recipientId);
        return new UnreadCountResponse(recipientId, notificationRepository.countByRecipientIdAndReadFalse(recipientId));
    }

    @Override
    public NotificationResponse markAsRead(String authorizationHeader, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        ensureOwnerOrAdmin(authorizationHeader, notification.getRecipientId());
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(java.time.Instant.now());
        }
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    public void markAllRead(String authorizationHeader, Long recipientId) {
        ensureOwnerOrAdmin(authorizationHeader, recipientId);
        List<Notification> unreadNotifications = notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(recipientId);
        if (unreadNotifications.isEmpty()) {
            return;
        }
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(java.time.Instant.now());
        });
        notificationRepository.saveAll(unreadNotifications);
    }

    @Override
    public void deleteNotification(String authorizationHeader, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        ensureOwnerOrAdmin(authorizationHeader, notification.getRecipientId());
        notificationRepository.delete(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll(String authorizationHeader) {
        ensureAdmin(authorizationHeader);
        return notificationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private NotificationResponse createAndSend(
            Long recipientId,
            Long actorId,
            NotificationType actionType,
            NotificationTargetType targetType,
            Long targetId,
            String message
    ) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setActorId(actorId);
        notification.setActionType(actionType);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setMessage(message.trim());
        return toResponse(notificationRepository.save(notification));
    }

    private void ensureOwnerOrAdmin(String authorizationHeader, Long recipientId) {
        AuthProfileResponse profile = resolveCurrentProfile(authorizationHeader);
        if (!Objects.equals(profile.userId(), recipientId) && !isAdmin(profile)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this notification");
        }
    }

    private void ensureAdmin(String authorizationHeader) {
        if (!isAdmin(resolveCurrentProfile(authorizationHeader))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
    }

    private boolean isAdmin(AuthProfileResponse profile) {
        return profile.role() != null && profile.role().equalsIgnoreCase("ADMIN");
    }

    private AuthProfileResponse resolveCurrentProfile(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            AuthProfileResponse profile = restTemplate.exchange(
                    authServiceUrl + "/auth/profile",
                    HttpMethod.GET,
                    request,
                    AuthProfileResponse.class
            ).getBody();
            if (profile == null || profile.userId() == null || !profile.active()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
            }
            return profile;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
    }

    private UserResponse fetchUserById(String authorizationHeader, Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            UserResponse user = restTemplate.exchange(
                    authServiceUrl + "/auth/users/" + userId,
                    HttpMethod.GET,
                    request,
                    UserResponse.class
            ).getBody();
            if (user == null || !user.active()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            return user;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private void sendEmailToUser(String recipientEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to send email notification");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getRecipientId(),
                notification.getActorId(),
                notification.getActionType(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
                , notification.getDeepLink()
        );
    }
}
