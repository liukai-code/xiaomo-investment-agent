package com.xiaomo.agent.conversation.service.impl;

import com.xiaomo.agent.conversation.entity.Conversation;
import com.xiaomo.agent.conversation.repository.ConversationRepository;
import com.xiaomo.agent.conversation.service.ChatHistoryCacheService;
import com.xiaomo.agent.conversation.service.ConversationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
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
            log.debug("listConversations: 缓存命中, userId={}, count={}", userId, cached.size());
            return cached;
        }
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByPinnedAndUpdatedAt(userId);
        // 修复旧数据：pinned 为 null 的统一设为 false
        for (Conversation c : conversations) {
            if (c.getPinned() == null) {
                c.setPinned(false);
            }
        }
        log.info("listConversations: DB查询, userId={}, count={}, pinned={}", userId, conversations.size(),
                conversations.stream().filter(c -> Boolean.TRUE.equals(c.getPinned())).count());
        cacheService.cacheConversations(userId, conversations);
        return conversations;
    }

    @Override
    public Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + conversationId));
    }

    @Override
    public Conversation getConversationForUser(Long conversationId, Long userId) {
        Conversation conversation = getConversation(conversationId);
        checkOwnership(conversation, userId);
        return conversation;
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
        getConversationForUser(conversationId, userId);
        conversationRepository.deleteById(conversationId);
        cacheService.evictConversationList(userId);
        cacheService.evictMessageCache(conversationId);
    }

    @Override
    @Transactional
    public Conversation togglePin(Long userId, Long conversationId) {
        Conversation conversation = getConversationForUser(conversationId, userId);
        boolean oldValue = Boolean.TRUE.equals(conversation.getPinned());
        conversation.setPinned(!oldValue);
        Conversation saved = conversationRepository.save(conversation);
        log.info("togglePin: convId={}, userId={}, pinned {} -> {}, saved.id={}", conversationId, userId, oldValue, saved.getPinned(), saved.getId());
        cacheService.evictConversationList(userId);
        return saved;
    }
}
