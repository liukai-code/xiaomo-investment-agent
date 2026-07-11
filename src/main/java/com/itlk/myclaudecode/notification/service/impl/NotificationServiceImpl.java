package com.itlk.myclaudecode.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.notification.entity.Notification;
import com.itlk.myclaudecode.notification.repository.NotificationRepository;
import com.itlk.myclaudecode.notification.service.NotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final String REDIS_CHANNEL = "notification:broadcast";
    private static final String READ_SET_PREFIX = "notification:read:";

    @Resource
    private NotificationRepository notificationRepository;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public Notification create(String title, String content) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(content);
        notification = notificationRepository.save(notification);

        // 发布到 Redis Channel
        try {
            String json = objectMapper.writeValueAsString(notification);
            stringRedisTemplate.convertAndSend(REDIS_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("序列化通知失败", e);
        }

        return notification;
    }

    @Override
    public List<Notification> listAll() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Notification> listRecent(int limit) {
        return notificationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }

    @Override
    public long getUnreadCount(Long userId) {
        List<Notification> all = listRecent(100);
        if (all.isEmpty()) return 0;
        Set<Long> readIds = getReadIds(userId);
        return all.stream().filter(n -> !readIds.contains(n.getId())).count();
    }

    @Override
    public void markAsRead(Long userId, Long notificationId) {
        stringRedisTemplate.opsForSet().add(READ_SET_PREFIX + userId, String.valueOf(notificationId));
    }

    @Override
    public Set<Long> getReadIds(Long userId) {
        Set<String> members = stringRedisTemplate.opsForSet().members(READ_SET_PREFIX + userId);
        if (members == null) return Set.of();
        return members.stream().map(Long::valueOf).collect(Collectors.toSet());
    }
}
