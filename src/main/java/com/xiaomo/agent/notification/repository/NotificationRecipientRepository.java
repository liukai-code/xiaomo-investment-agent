package com.xiaomo.agent.notification.repository;

import com.xiaomo.agent.notification.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    List<NotificationRecipient> findByNotificationId(Long notificationId);

    List<NotificationRecipient> findByUserId(Long userId);

    boolean existsByNotificationIdAndUserId(Long notificationId, Long userId);

    void deleteByNotificationId(Long notificationId);
}
