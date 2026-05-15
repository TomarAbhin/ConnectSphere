package com.connectsphere.notification.serviceimpl;

import com.connectsphere.notification.dto.AuthProfileResponse;
import com.connectsphere.notification.dto.BulkNotificationRequest;
import com.connectsphere.notification.dto.CreateNotificationRequest;
import com.connectsphere.notification.dto.UserResponse;
import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RestTemplate restTemplate;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                restTemplate,
                "http://auth"
        );
    }

    @Test
    void createNotificationStoresTrimmedMessage() {
        UserResponse recipient = user(9L, "USER");
        when(restTemplate.exchange(
                eq("http://auth/auth/users/9"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserResponse.class)
        )).thenReturn(new ResponseEntity<>(recipient, HttpStatus.OK));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationRequest request = new CreateNotificationRequest(
                9L,
                4L,
                NotificationType.LIKE,
                NotificationTargetType.POST,
                55L,
                "  New like received  ",
                "/post/55"
        );

        var response = notificationService.createNotification("Bearer token", request);

        assertNotNull(response);
        assertEquals(9L, response.recipientId());
        assertEquals(4L, response.actorId());
        assertEquals("New like received", response.message());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals("New like received", notificationCaptor.getValue().getMessage());
    }

    @Test
    void ownerCanReadMarkAndDeleteNotifications() {
        stubProfile(profile(9L, "USER"));
        Notification notification = notification(1L, 9L, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(notification));
        when(notificationRepository.countByRecipientIdAndReadFalse(9L)).thenReturn(1L);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1L, notificationService.getNotificationById("Bearer token", 1L).notificationId());
        assertEquals(1, notificationService.getByRecipient("Bearer token", 9L).size());
        assertEquals(1L, notificationService.getUnreadCount("Bearer token", 9L).unreadCount());
        assertTrue(notificationService.markAsRead("Bearer token", 1L).read());
        assertNotNull(notification.getReadAt());
        notificationService.deleteNotification("Bearer token", 1L);
        verify(notificationRepository).delete(notification);
    }

    @Test
    void markAllReadSavesUnreadNotifications() {
        stubProfile(profile(9L, "USER"));
        Notification first = notification(1L, 9L, false);
        Notification second = notification(2L, 9L, false);
        when(notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(9L)).thenReturn(List.of(first, second));

        notificationService.markAllRead("Bearer token", 9L);

        assertTrue(first.isRead());
        assertTrue(second.isRead());
        verify(notificationRepository).saveAll(List.of(first, second));
    }

    @Test
    void adminCanSendBulkAndListAllNotifications() {
        stubProfile(profile(1L, "ADMIN"));
        when(restTemplate.exchange(eq("http://auth/auth/users/9"), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(new ResponseEntity<>(user(9L, "USER"), HttpStatus.OK));
        when(restTemplate.exchange(eq("http://auth/auth/users/10"), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(new ResponseEntity<>(user(10L, "USER"), HttpStatus.OK));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Notification stored = notification(3L, 9L, false);
        when(notificationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(stored));

        var responses = notificationService.sendBulkNotification(
                "Bearer admin",
                new BulkNotificationRequest(
                        List.of(9L, 9L, 10L),
                        1L,
                        NotificationType.FOLLOW,
                        NotificationTargetType.USER,
                        10L,
                        " hello "
                )
        );

        assertEquals(2, responses.size());
        assertEquals(1, notificationService.getAll("Bearer admin").size());
    }

    private void stubProfile(AuthProfileResponse profile) {
        when(restTemplate.exchange(
                eq("http://auth/auth/profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AuthProfileResponse.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));
    }

    private AuthProfileResponse profile(Long userId, String role) {
        return new AuthProfileResponse(
                userId,
                "user" + userId,
                "user" + userId + "@example.com",
                "User " + userId,
                null,
                null,
                role,
                "LOCAL",
                true,
                Instant.parse("2026-05-05T00:00:00Z")
        );
    }

    private UserResponse user(Long userId, String role) {
        return new UserResponse(
                userId,
                "user" + userId,
                "user" + userId + "@example.com",
                "User " + userId,
                null,
                null,
                role,
                "LOCAL",
                true
        );
    }

    private Notification notification(Long id, Long recipientId, boolean read) {
        Notification notification = new Notification();
        notification.setNotificationId(id);
        notification.setRecipientId(recipientId);
        notification.setActorId(4L);
        notification.setActionType(NotificationType.LIKE);
        notification.setTargetType(NotificationTargetType.POST);
        notification.setTargetId(55L);
        notification.setMessage("message");
        notification.setRead(read);
        return notification;
    }
}
