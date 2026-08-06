package com.xiaomo.agent.conversation.service;

import com.xiaomo.agent.conversation.entity.Conversation;
import java.util.List;

public interface ConversationService {

    Conversation createConversation(Long userId, String title);

    List<Conversation> listConversations(Long userId);

    Conversation getConversation(Long conversationId);

    Conversation getConversationForUser(Long conversationId, Long userId);

    void checkOwnership(Conversation conversation, Long userId);

    void deleteConversation(Long userId, Long conversationId);

    Conversation togglePin(Long userId, Long conversationId);
}
