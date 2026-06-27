package com.itlk.myclaudecode.agent.service;

import com.itlk.myclaudecode.agent.Entity.Conversation;
import java.util.List;

public interface ConversationService {

    Conversation createConversation(Long userId, String title);

    List<Conversation> listConversations(Long userId);

    Conversation getConversation(Long conversationId);

    void checkOwnership(Conversation conversation, Long userId);
}
