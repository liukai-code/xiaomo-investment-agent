package com.itlk.myclaudecode.notification.repository;

import com.itlk.myclaudecode.notification.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    List<NotificationRecipient> findByNotificationId(Long notificationId);

    List<NotificationRecipient> findByUserId(Long userId);

    boolean existsByNotificationIdAndUserId(Long notificationId, Long userId);

    void deleteByNotificationId(Long notificationId);
}
