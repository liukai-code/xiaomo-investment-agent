package com.xiaomo.agent.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.notification.entity.Notification;
import com.xiaomo.agent.notification.entity.NotificationRecipient;
import com.xiaomo.agent.notification.repository.NotificationRecipientRepository;
import com.xiaomo.agent.notification.repository.NotificationRepository;
import com.xiaomo.agent.notification.service.NotificationService;
import com.xiaomo.agent.user.entity.User;
import com.xiaomo.agent.user.repository.UserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final String REDIS_CHANNEL = "notification:broadcast";
    private static final String READ_SET_PREFIX = "notification:read:";
    private static final String HIDDEN_SET_PREFIX = "notification:hidden:";

    @Resource
    private NotificationRepository notificationRepository;

    @Resource
    private NotificationRecipientRepository notificationRecipientRepository;

    @Resource
    private UserRepository userRepository;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public Notification create(String title, String content) {
        return create(title, content, null);
    }

    @Override
    @Transactional
    public Notification create(String title, String content, List<Long> targetUserIds) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(content);

        boolean isBroadcast = targetUserIds == null || targetUserIds.isEmpty();
        notification.setBroadcast(isBroadcast);
        Notification saved = notificationRepository.save(notification);

        // 定向通知：为每个目标用户创建 recipient 记录
        if (!isBroadcast) {
            Long notificationId = saved.getId();
            List<NotificationRecipient> recipients = targetUserIds.stream()
                    .map(userId -> {
                        NotificationRecipient r = new NotificationRecipient();
                        r.setNotificationId(notificationId);
                        r.setUserId(userId);
                        return r;
                    })
                    .collect(Collectors.toList());
            notificationRecipientRepository.saveAll(recipients);
        }

        // 发布到 Redis Channel，携带 broadcast 和 targetUserIds 信息
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("id", saved.getId());
            message.put("title", saved.getTitle());
            message.put("content", saved.getContent());
            message.put("createdAt", saved.getCreatedAt());
            message.put("broadcast", isBroadcast);
            if (!isBroadcast) {
                message.put("targetUserIds", targetUserIds);
            }
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(REDIS_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("序列化通知失败", e);
        }

        return saved;
    }

    @Override
    public List<Long> getTargetUsers(Long notificationId) {
        return notificationRecipientRepository.findByNotificationId(notificationId)
                .stream()
                .map(NotificationRecipient::getUserId)
                .collect(Collectors.toList());
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
        notificationRecipientRepository.deleteByNotificationId(id);
        notificationRepository.deleteById(id);
    }

    @Override
    public long getUnreadCount(Long userId) {
        List<Notification> all = listRecentForUser(userId, 100);
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

    @Override
    public void hideNotification(Long userId, Long notificationId) {
        stringRedisTemplate.opsForSet().add(HIDDEN_SET_PREFIX + userId, String.valueOf(notificationId));
    }

    @Override
    public Set<Long> getHiddenIds(Long userId) {
        Set<String> members = stringRedisTemplate.opsForSet().members(HIDDEN_SET_PREFIX + userId);
        if (members == null) return Set.of();
        return members.stream().map(Long::valueOf).collect(Collectors.toSet());
    }

    @Override
    public List<Notification> listRecentForUser(Long userId, int limit) {
        // 获取用户注册时间，只返回注册之后的通知
        LocalDateTime registeredAt = userRepository.findById(userId)
                .map(User::getCreatedAt)
                .orElse(null);

        // 获取该用户定向通知的 ID 列表
        List<Long> targetedNotificationIds = notificationRecipientRepository.findByUserId(userId)
                .stream()
                .map(NotificationRecipient::getNotificationId)
                .collect(Collectors.toList());

        // 查询：广播通知（注册后） + 该用户的定向通知
        List<Notification> all;
        if (registeredAt != null) {
            all = targetedNotificationIds.isEmpty()
                    ? notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(registeredAt, PageRequest.of(0, limit))
                    : notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrIdInOrderByCreatedAtDesc(registeredAt, targetedNotificationIds, PageRequest.of(0, limit));
        } else {
            // 找不到用户时降级为不过滤注册时间
            all = targetedNotificationIds.isEmpty()
                    ? notificationRepository.findAllByBroadcastTrueOrderByCreatedAtDesc(PageRequest.of(0, limit))
                    : notificationRepository.findAllByBroadcastTrueOrIdInOrderByCreatedAtDesc(targetedNotificationIds, PageRequest.of(0, limit));
        }

        // 过滤隐藏的通知
        Set<Long> hiddenIds = getHiddenIds(userId);
        if (hiddenIds.isEmpty()) return all;
        return all.stream().filter(n -> !hiddenIds.contains(n.getId())).collect(Collectors.toList());
    }
}
