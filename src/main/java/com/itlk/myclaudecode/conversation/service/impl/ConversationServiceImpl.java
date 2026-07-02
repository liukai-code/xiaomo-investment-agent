package com.itlk.myclaudecode.conversation.service.impl;

import com.itlk.myclaudecode.conversation.entity.Conversation;
import com.itlk.myclaudecode.conversation.repository.ConversationRepository;
import com.itlk.myclaudecode.conversation.service.ChatHistoryCacheService;
import com.itlk.myclaudecode.conversation.service.ConversationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ChatHistoryCacheService cacheService;

    @Override
    @Transactional
    public Conversation createConversation(Long userId, String title) {
        String effectiveTitle = title != null ? title : "新对话";
        Conversation existing = conversationRepository.findFirstByUserIdAndTitleOrderByUpdatedAtDesc(userId, effectiveTitle);
        if (existing != null) {
            return existing;
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(effectiveTitle);
        Conversation saved = conversationRepository.save(conversation);
        cacheService.evictConversationList(userId);
        return saved;
    }

    @Override
    public List<Conversation> listConversations(Long userId) {
        List<Conversation> cached = cacheService.getCachedConversations(userId);
        if (cached != null) {
            return cached;
        }
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        cacheService.cacheConversations(userId, conversations);
        return conversations;
    }

    @Override
    public Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + conversationId));
    }

    @Override
    public void checkOwnership(Conversation conversation, Long userId) {
        if (conversation.getUserId() != null && !conversation.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该会话");
        }
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = getConversation(conversationId);
        checkOwnership(conversation, userId);
        conversationRepository.deleteById(conversationId);
        cacheService.evictConversationList(userId);
        cacheService.evictMessageCache(conversationId);
    }
}
