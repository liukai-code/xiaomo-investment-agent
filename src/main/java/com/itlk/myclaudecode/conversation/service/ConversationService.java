package com.itlk.myclaudecode.conversation.service;

import com.itlk.myclaudecode.conversation.entity.Conversation;
import java.util.List;

public interface ConversationService {

    Conversation createConversation(Long userId, String title);

    List<Conversation> listConversations(Long userId);

    Conversation getConversation(Long conversationId);

    void checkOwnership(Conversation conversation, Long userId);

    void deleteConversation(Long userId, Long conversationId);
}
