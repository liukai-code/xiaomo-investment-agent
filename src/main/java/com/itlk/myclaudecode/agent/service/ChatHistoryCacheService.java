package com.itlk.myclaudecode.agent.service;

import com.itlk.myclaudecode.agent.Entity.ChatMessage;
import com.itlk.myclaudecode.agent.Entity.Conversation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChatHistoryCacheService {

    private static final String CONV_LIST_PREFIX = "cache:conv:list:";
    private static final String MSGS_PREFIX = "cache:conv:msgs:";
    private static final String RECENT_PREFIX = "cache:conv:recent:";

    private static final long CONV_LIST_TTL_MINUTES = 30;
    private static final long MSGS_TTL_HOURS = 1;
    private static final long RECENT_TTL_MINUTES = 10;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ========== 会话列表缓存 ==========

    public List<Conversation> getCachedConversations(Long userId) {
        String key = CONV_LIST_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Conversation) {
            log.debug("会话列表缓存命中: userId={}", userId);
            @SuppressWarnings("unchecked")
            List<Conversation> result = (List<Conversation>) (List<?>) list;
            return result;
        }
        return null;
    }

    public void cacheConversations(Long userId, List<Conversation> conversations) {
        String key = CONV_LIST_PREFIX + userId;
        redisTemplate.opsForValue().set(key, conversations, CONV_LIST_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("会话列表已缓存: userId={}, count={}", userId, conversations.size());
    }

    // ========== 消息列表缓存 ==========

    public List<ChatMessage> getCachedMessages(Long convId) {
        String key = MSGS_PREFIX + convId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ChatMessage) {
            log.debug("消息列表缓存命中: convId={}", convId);
            @SuppressWarnings("unchecked")
            List<ChatMessage> result = (List<ChatMessage>) (List<?>) list;
            return result;
        }
        return null;
    }

    public void cacheMessages(Long convId, List<ChatMessage> messages) {
        String key = MSGS_PREFIX + convId;
        redisTemplate.opsForValue().set(key, messages, MSGS_TTL_HOURS, TimeUnit.HOURS);
        log.debug("消息列表已缓存: convId={}, count={}", convId, messages.size());
    }

    // ========== 最近消息缓存 ==========

    public List<ChatMessage> getCachedRecentMessages(Long convId, int limit) {
        String key = RECENT_PREFIX + convId + ":" + limit;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ChatMessage) {
            log.debug("最近消息缓存命中: convId={}, limit={}", convId, limit);
            @SuppressWarnings("unchecked")
            List<ChatMessage> result = (List<ChatMessage>) (List<?>) list;
            return result;
        }
        return null;
    }

    public void cacheRecentMessages(Long convId, int limit, List<ChatMessage> messages) {
        String key = RECENT_PREFIX + convId + ":" + limit;
        redisTemplate.opsForValue().set(key, messages, RECENT_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("最近消息已缓存: convId={}, limit={}, count={}", convId, limit, messages.size());
    }

    // ========== 缓存失效 ==========

    public void evictConversationList(Long userId) {
        String key = CONV_LIST_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("会话列表缓存已失效: userId={}", userId);
    }

    public void evictMessageCache(Long convId) {
        // 失效消息列表
        redisTemplate.delete(MSGS_PREFIX + convId);

        // 失效所有 recent 缓存
        Set<String> recentKeys = redisTemplate.keys(RECENT_PREFIX + convId + ":*");
        if (recentKeys != null && !recentKeys.isEmpty()) {
            redisTemplate.delete(recentKeys);
            log.debug("消息缓存已失效: convId={}, 清除 {} 个 recent key", convId, recentKeys.size());
        } else {
            log.debug("消息缓存已失效: convId={}", convId);
        }
    }
}
