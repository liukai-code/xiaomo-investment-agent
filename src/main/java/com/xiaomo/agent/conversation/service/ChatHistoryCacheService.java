package com.xiaomo.agent.conversation.service;

import com.xiaomo.agent.conversation.entity.ChatMessage;
import com.xiaomo.agent.conversation.entity.Conversation;
import java.util.List;

public interface ChatHistoryCacheService {

    List<Conversation> getCachedConversations(Long userId);

    void cacheConversations(Long userId, List<Conversation> conversations);

    List<ChatMessage> getCachedMessages(Long convId);

    void cacheMessages(Long convId, List<ChatMessage> messages);

    List<ChatMessage> getCachedRecentMessages(Long convId, int limit);

    void cacheRecentMessages(Long convId, int limit, List<ChatMessage> messages);

    void evictConversationList(Long userId);

    void evictMessageCache(Long convId);
}
