package com.xiaomo.agent.conversation.service;

import com.xiaomo.agent.conversation.entity.ChatMessage;
import com.xiaomo.agent.conversation.entity.Conversation;
import com.xiaomo.agent.conversation.entity.MessageRole;
import java.util.List;

public interface ChatMessageService {

    void saveMessage(Conversation conversation, MessageRole role, String content,
                     String toolName, String toolCallId);

    void saveAssistantMessage(Long userId, Long conversationId, String content);

    List<ChatMessage> getHistory(Long userId, Long conversationId);
}
