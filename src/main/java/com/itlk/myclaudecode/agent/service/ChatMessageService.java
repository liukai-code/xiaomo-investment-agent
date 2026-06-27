package com.itlk.myclaudecode.agent.service;

import com.itlk.myclaudecode.agent.Entity.ChatMessage;
import com.itlk.myclaudecode.agent.Entity.Conversation;
import com.itlk.myclaudecode.agent.Entity.MessageRole;
import java.util.List;

public interface ChatMessageService {

    void saveMessage(Conversation conversation, MessageRole role, String content,
                     String toolName, String toolCallId);

    void saveAssistantMessage(Long conversationId, String content);

    List<ChatMessage> getHistory(Long userId, Long conversationId);
}
