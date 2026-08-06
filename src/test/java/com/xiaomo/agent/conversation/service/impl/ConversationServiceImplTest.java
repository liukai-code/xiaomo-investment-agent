package com.xiaomo.agent.conversation.service.impl;

import com.xiaomo.agent.conversation.entity.Conversation;
import com.xiaomo.agent.conversation.repository.ConversationRepository;
import com.xiaomo.agent.conversation.service.ChatHistoryCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationServiceImpl 会话管理测试")
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatHistoryCacheService cacheService;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    // ========== createConversation ==========

    @Nested
    @DisplayName("createConversation 创建会话")
    class CreateConversationTest {

        @Test
        @DisplayName("新建会话 → 保存并清缓存")
        void createNew() {
            when(conversationRepository.findFirstByUserIdAndTitleOrderByUpdatedAtDesc(1L, "新对话"))
                    .thenReturn(null);
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
                Conversation c = inv.getArgument(0);
                c.setId(100L);
                return c;
            });

            Conversation result = conversationService.createConversation(1L, "新对话");

            assertNotNull(result, "应返回会话");
            assertEquals(100L, result.getId(), "应有ID");
            assertEquals("新对话", result.getTitle(), "标题应为新对话");
            verify(cacheService).evictConversationList(1L);
        }

        @Test
        @DisplayName("同userId+title已存在 → 返回已有会话（去重）")
        void dedupExisting() {
            Conversation existing = new Conversation();
            existing.setId(50L);
            existing.setTitle("分析茅台");
            existing.setUserId(1L);

            when(conversationRepository.findFirstByUserIdAndTitleOrderByUpdatedAtDesc(1L, "分析茅台"))
                    .thenReturn(existing);

            Conversation result = conversationService.createConversation(1L, "分析茅台");

            assertEquals(50L, result.getId(), "应返回已有会话");
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("title为null → 默认为'新对话'")
        void nullTitleDefaults() {
            when(conversationRepository.findFirstByUserIdAndTitleOrderByUpdatedAtDesc(1L, "新对话"))
                    .thenReturn(null);
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
                Conversation c = inv.getArgument(0);
                c.setId(101L);
                return c;
            });

            Conversation result = conversationService.createConversation(1L, null);

            assertEquals("新对话", result.getTitle(), "null标题应默认为'新对话'");
        }
    }

    // ========== listConversations ==========

    @Nested
    @DisplayName("listConversations 会话列表")
    class ListConversationsTest {

        @Test
        @DisplayName("缓存命中 → 直接返回缓存数据")
        void cacheHit() {
            List<Conversation> cached = List.of(new Conversation());
            when(cacheService.getCachedConversations(1L)).thenReturn(cached);

            List<Conversation> result = conversationService.listConversations(1L);

            assertEquals(cached, result, "应返回缓存数据");
            verify(conversationRepository, never()).findByUserIdOrderByPinnedAndUpdatedAt(any());
        }

        @Test
        @DisplayName("缓存未命中 → 查DB并缓存")
        void cacheMiss() {
            List<Conversation> dbResult = List.of(new Conversation());
            when(cacheService.getCachedConversations(1L)).thenReturn(null);
            when(conversationRepository.findByUserIdOrderByPinnedAndUpdatedAt(1L)).thenReturn(dbResult);

            List<Conversation> result = conversationService.listConversations(1L);

            assertEquals(dbResult, result, "应返回DB数据");
            verify(cacheService).cacheConversations(1L, dbResult);
        }
    }

    // ========== getConversation ==========

    @Nested
    @DisplayName("getConversation 获取单个会话")
    class GetConversationTest {

        @Test
        @DisplayName("会话存在 → 返回会话")
        void found() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

            Conversation result = conversationService.getConversation(1L);
            assertEquals(1L, result.getId());
        }

        @Test
        @DisplayName("会话不存在 → 抛出异常")
        void notFound() {
            when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> conversationService.getConversation(999L),
                    "不存在的会话应抛异常");
        }
    }

    // ========== getConversationForUser ==========

    @Nested
    @DisplayName("getConversationForUser 带权限的会话获取")
    class GetConversationForUserTest {

        @Test
        @DisplayName("有权限 → 返回会话")
        void authorized() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            conv.setUserId(1L);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

            Conversation result = conversationService.getConversationForUser(1L, 1L);
            assertEquals(1L, result.getId());
        }

        @Test
        @DisplayName("无权限 → 抛出异常")
        void unauthorized() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            conv.setUserId(1L);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

            assertThrows(RuntimeException.class,
                    () -> conversationService.getConversationForUser(1L, 999L),
                    "无权访问应抛异常");
        }
    }

    // ========== deleteConversation ==========

    @Nested
    @DisplayName("deleteConversation 删除会话")
    class DeleteConversationTest {

        @Test
        @DisplayName("有权限 → 删除并清缓存")
        void authorizedDelete() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            conv.setUserId(1L);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

            assertDoesNotThrow(() -> conversationService.deleteConversation(1L, 1L),
                    "有权限删除不应抛异常");
            verify(conversationRepository).deleteById(1L);
            verify(cacheService).evictConversationList(1L);
            verify(cacheService).evictMessageCache(1L);
        }

        @Test
        @DisplayName("无权限 → 抛出异常不删除")
        void unauthorizedDelete() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            conv.setUserId(1L);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

            assertThrows(RuntimeException.class,
                    () -> conversationService.deleteConversation(999L, 1L));
            verify(conversationRepository, never()).deleteById(any());
        }
    }
}
