package com.xiaomo.agent.notification.service;

import com.xiaomo.agent.notification.entity.Notification;

import java.util.List;
import java.util.Set;

public interface NotificationService {

    Notification create(String title, String content);

    Notification create(String title, String content, List<Long> targetUserIds);

    List<Long> getTargetUsers(Long notificationId);

    List<Notification> listAll();

    List<Notification> listRecent(int limit);

    void delete(Long id);

    long getUnreadCount(Long userId);

    void markAsRead(Long userId, Long notificationId);

    Set<Long> getReadIds(Long userId);

    void hideNotification(Long userId, Long notificationId);

    Set<Long> getHiddenIds(Long userId);

    List<Notification> listRecentForUser(Long userId, int limit);
}
