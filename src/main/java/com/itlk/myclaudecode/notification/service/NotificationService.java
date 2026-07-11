package com.itlk.myclaudecode.notification.service;

import com.itlk.myclaudecode.notification.entity.Notification;

import java.util.List;
import java.util.Set;

public interface NotificationService {

    Notification create(String title, String content);

    List<Notification> listAll();

    List<Notification> listRecent(int limit);

    void delete(Long id);

    long getUnreadCount(Long userId);

    void markAsRead(Long userId, Long notificationId);

    Set<Long> getReadIds(Long userId);
}
