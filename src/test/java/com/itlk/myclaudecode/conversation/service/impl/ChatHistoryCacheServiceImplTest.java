package com.itlk.myclaudecode.conversation.service.impl;

import com.itlk.myclaudecode.conversation.entity.ChatMessage;
import com.itlk.myclaudecode.conversation.entity.Conversation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatHistoryCacheServiceImpl 缓存服务测试")
class ChatHistoryCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private ChatHistoryCacheServiceImpl cacheService;

    // ========== getCachedConversations ==========

    @Nested
    @DisplayName("getCachedConversations 获取会话列表缓存")
    class GetCachedConversationsTest {

        @Test
        @DisplayName("有缓存 → 返回列表")
        void cacheHit() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            List<Conversation> cached = List.of(conv);

            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:list:1")).thenReturn(cached);

            List<Conversation> result = cacheService.getCachedConversations(1L);
            assertNotNull(result, "缓存命中应返回数据");
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("无缓存 → 返回null")
        void cacheMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:list:1")).thenReturn(null);

            List<Conversation> result = cacheService.getCachedConversations(1L);
            assertNull(result, "缓存未命中应返回null");
        }

        @Test
        @DisplayName("缓存类型不匹配 → 返回null")
        void typeMismatch() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:list:1")).thenReturn("not-a-list");

            List<Conversation> result = cacheService.getCachedConversations(1L);
            assertNull(result, "类型不匹配应返回null");
        }
    }

    // ========== cacheConversations ==========

    @Nested
    @DisplayName("cacheConversations 缓存会话列表")
    class CacheConversationsTest {

        @Test
        @DisplayName("写入Redis并设置30分钟TTL")
        void writeWithTTL() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            List<Conversation> data = List.of(new Conversation());

            cacheService.cacheConversations(1L, data);

            verify(valueOps).set("cache:conv:list:1", data, 30, TimeUnit.MINUTES);
        }
    }

    // ========== getCachedMessages / cacheMessages ==========

    @Nested
    @DisplayName("getCachedMessages 获取消息缓存")
    class GetCachedMessagesTest {

        @Test
        @DisplayName("有缓存 → 返回消息列表")
        void cacheHit() {
            ChatMessage msg = new ChatMessage();
            msg.setId(1L);
            List<ChatMessage> cached = List.of(msg);

            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:msgs:100")).thenReturn(cached);

            List<ChatMessage> result = cacheService.getCachedMessages(100L);
            assertNotNull(result, "缓存命中应返回数据");
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("无缓存 → 返回null")
        void cacheMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:msgs:100")).thenReturn(null);

            assertNull(cacheService.getCachedMessages(100L));
        }
    }

    @Nested
    @DisplayName("cacheMessages 缓存消息列表")
    class CacheMessagesTest {

        @Test
        @DisplayName("写入Redis并设置1小时TTL")
        void writeWithTTL() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            List<ChatMessage> data = List.of(new ChatMessage());
            cacheService.cacheMessages(100L, data);

            verify(valueOps).set("cache:conv:msgs:100", data, 1, TimeUnit.HOURS);
        }
    }

    // ========== getCachedRecentMessages / cacheRecentMessages ==========

    @Nested
    @DisplayName("getCachedRecentMessages 获取最近消息缓存")
    class GetCachedRecentMessagesTest {

        @Test
        @DisplayName("有缓存 → 返回消息列表")
        void cacheHit() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:recent:100:10")).thenReturn(List.of(new ChatMessage()));

            List<ChatMessage> result = cacheService.getCachedRecentMessages(100L, 10);
            assertNotNull(result, "缓存命中应返回数据");
            assertEquals(1, result.size(), "应返回1条消息");
        }

        @Test
        @DisplayName("无缓存 → 返回null")
        void cacheMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("cache:conv:recent:100:10")).thenReturn(null);

            assertNull(cacheService.getCachedRecentMessages(100L, 10));
        }
    }

    @Nested
    @DisplayName("cacheRecentMessages 缓存最近消息")
    class CacheRecentMessagesTest {

        @Test
        @DisplayName("写入Redis并设置10分钟TTL")
        void writeWithTTL() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            List<ChatMessage> data = List.of(new ChatMessage());
            cacheService.cacheRecentMessages(100L, 10, data);

            verify(valueOps).set("cache:conv:recent:100:10", data, 10, TimeUnit.MINUTES);
        }
    }

    // ========== evictConversationList ==========

    @Nested
    @DisplayName("evictConversationList 清除会话列表缓存")
    class EvictConversationListTest {

        @Test
        @DisplayName("删除对应key")
        void deleteKey() {
            cacheService.evictConversationList(1L);
            verify(redisTemplate).delete("cache:conv:list:1");
        }
    }

    // ========== evictMessageCache ==========

    @Nested
    @DisplayName("evictMessageCache 清除消息缓存")
    class EvictMessageCacheTest {

        @Test
        @DisplayName("删除主key和所有recent keys")
        void deleteAllKeys() {
            Set<String> recentKeys = Set.of(
                    "cache:conv:recent:100:10",
                    "cache:conv:recent:100:20"
            );
            when(redisTemplate.keys("cache:conv:recent:100:*")).thenReturn(recentKeys);

            cacheService.evictMessageCache(100L);

            verify(redisTemplate).delete("cache:conv:msgs:100");
            verify(redisTemplate).delete(recentKeys);
        }

        @Test
        @DisplayName("无recent keys → 只删主key")
        void noRecentKeys() {
            when(redisTemplate.keys("cache:conv:recent:100:*")).thenReturn(null);

            cacheService.evictMessageCache(100L);

            verify(redisTemplate).delete("cache:conv:msgs:100");
            verify(redisTemplate, never()).delete((Set<String>) any());
        }
    }
}
