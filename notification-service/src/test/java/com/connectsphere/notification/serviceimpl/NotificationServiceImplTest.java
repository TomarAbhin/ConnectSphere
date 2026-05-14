package com.connectsphere.notification.serviceimpl;

import com.connectsphere.notification.dto.CreateNotificationRequest;
import com.connectsphere.notification.dto.UserResponse;
import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.repository.NotificationRepository;
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
        UserResponse recipient = new UserResponse(
                9L,
                "recipient",
                "recipient@example.com",
                "Recipient User",
                null,
                null,
                "USER",
                "LOCAL",
                true
        );

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
}