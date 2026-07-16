package com.itlk.myclaudecode.conversation.service.impl;

import com.itlk.myclaudecode.conversation.entity.ChatMessage;
import com.itlk.myclaudecode.conversation.entity.Conversation;
import com.itlk.myclaudecode.conversation.entity.MessageRole;
import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.service.ChatHistoryCacheService;
import com.itlk.myclaudecode.conversation.service.ConversationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageServiceImpl 消息管理测试")
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ChatHistoryCacheService cacheService;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    // ========== saveMessage ==========

    @Nested
    @DisplayName("saveMessage 保存消息")
    class SaveMessageTest {

        @Test
        @DisplayName("保存消息 → 持久化并清缓存")
        void saveAndEvictCache() {
            Conversation conv = new Conversation();
            conv.setId(1L);

            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
                ChatMessage msg = inv.getArgument(0);
                msg.setId(100L);
                return msg;
            });

            chatMessageService.saveMessage(conv, MessageRole.USER, "你好", null, null);

            verify(chatMessageRepository).save(argThat(msg ->
                    msg.getRole() == MessageRole.USER
                            && "你好".equals(msg.getContent())
                            && msg.getConversation().getId().equals(1L)
            ));
            verify(cacheService).evictMessageCache(1L);
        }

        @Test
        @DisplayName("带工具信息保存 → 工具字段正确")
        void saveWithToolInfo() {
            Conversation conv = new Conversation();
            conv.setId(1L);

            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            chatMessageService.saveMessage(conv, MessageRole.ASSISTANT, "结果", "a_stock_quote", "call-123");

            verify(chatMessageRepository).save(argThat(msg ->
                    msg.getToolName().equals("a_stock_quote") && msg.getToolCallId().equals("call-123")
            ));
        }
    }

    // ========== saveAssistantMessage ==========

    @Nested
    @DisplayName("saveAssistantMessage 保存助手消息")
    class SaveAssistantMessageTest {

        @Test
        @DisplayName("有权限 → 保存助手消息")
        void authorizedSave() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            when(conversationService.getConversationForUser(1L, 1L)).thenReturn(conv);
            when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            chatMessageService.saveAssistantMessage(1L, 1L, "分析结果");

            verify(chatMessageRepository).save(argThat(msg ->
                    msg.getRole() == MessageRole.ASSISTANT && "分析结果".equals(msg.getContent())
            ));
        }

        @Test
        @DisplayName("无权限 → conversationService抛异常传播")
        void unauthorizedThrows() {
            when(conversationService.getConversationForUser(1L, 999L))
                    .thenThrow(new RuntimeException("无权访问该会话"));

            assertThrows(RuntimeException.class,
                    () -> chatMessageService.saveAssistantMessage(999L, 1L, "内容"));
        }
    }

    // ========== getHistory ==========

    @Nested
    @DisplayName("getHistory 获取历史消息")
    class GetHistoryTest {

        @Test
        @DisplayName("缓存命中 → 直接返回")
        void cacheHit() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            when(conversationService.getConversationForUser(1L, 1L)).thenReturn(conv);

            List<ChatMessage> cached = List.of(new ChatMessage());
            when(cacheService.getCachedMessages(1L)).thenReturn(cached);

            List<ChatMessage> result = chatMessageService.getHistory(1L, 1L);

            assertEquals(cached, result, "应返回缓存数据");
            verify(chatMessageRepository, never()).findByConversationIdOrderByIdAsc(any());
        }

        @Test
        @DisplayName("缓存未命中 → 查DB并缓存")
        void cacheMiss() {
            Conversation conv = new Conversation();
            conv.setId(1L);
            when(conversationService.getConversationForUser(1L, 1L)).thenReturn(conv);
            when(cacheService.getCachedMessages(1L)).thenReturn(null);

            List<ChatMessage> dbResult = List.of(new ChatMessage(), new ChatMessage());
            when(chatMessageRepository.findByConversationIdOrderByIdAsc(1L)).thenReturn(dbResult);

            List<ChatMessage> result = chatMessageService.getHistory(1L, 1L);

            assertEquals(2, result.size(), "应返回DB数据");
            verify(cacheService).cacheMessages(1L, dbResult);
        }
    }
}
