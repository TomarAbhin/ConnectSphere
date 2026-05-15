package com.connectsphere.notification.repository;

import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.entity.NotificationTargetType;
import com.connectsphere.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    Optional<Notification> findByNotificationIdAndRecipientId(Long notificationId, Long recipientId);

    List<Notification> findAllByOrderByCreatedAtDesc();

    boolean existsByRecipientIdAndActorIdAndActionTypeAndTargetTypeAndTargetId(
            Long recipientId,
            Long actorId,
            NotificationType actionType,
            NotificationTargetType targetType,
            Long targetId
    );
}
