package com.itlk.myclaudecode.conversation.service.impl;

import com.itlk.myclaudecode.conversation.entity.ChatMessage;
import com.itlk.myclaudecode.conversation.entity.Conversation;
import com.itlk.myclaudecode.conversation.entity.MessageRole;
import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.repository.ConversationRepository;
import com.itlk.myclaudecode.conversation.service.ChatHistoryCacheService;
import com.itlk.myclaudecode.conversation.service.ChatMessageService;
import com.itlk.myclaudecode.conversation.service.ConversationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ConversationService conversationService;

    @Resource
    private ChatHistoryCacheService cacheService;

    @Override
    @Transactional
    public void saveMessage(Conversation conversation, MessageRole role, String content,
                            String toolName, String toolCallId) {
        ChatMessage msg = new ChatMessage();
        msg.setConversation(conversation);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolName(toolName);
        msg.setToolCallId(toolCallId);
        chatMessageRepository.save(msg);
        cacheService.evictMessageCache(conversation.getId());
    }

    @Override
    @Transactional
    public void saveAssistantMessage(Long userId, Long conversationId, String content) {
        Conversation conversation = conversationService.getConversationForUser(conversationId, userId);
        saveMessage(conversation, MessageRole.ASSISTANT, content, null, null);
    }

    @Override
    public List<ChatMessage> getHistory(Long userId, Long conversationId) {
        conversationService.getConversationForUser(conversationId, userId);

        List<ChatMessage> cached = cacheService.getCachedMessages(conversationId);
        if (cached != null) {
            return cached;
        }
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByIdAsc(conversationId);
        cacheService.cacheMessages(conversationId, messages);
        return messages;
    }
}
