package com.itlk.myclaudecode.conversation.service;

import com.itlk.myclaudecode.conversation.entity.ChatMessage;
import com.itlk.myclaudecode.conversation.entity.Conversation;
import com.itlk.myclaudecode.conversation.entity.MessageRole;
import java.util.List;

public interface ChatMessageService {

    void saveMessage(Conversation conversation, MessageRole role, String content,
                     String toolName, String toolCallId);

    void saveAssistantMessage(Long userId, Long conversationId, String content);

    List<ChatMessage> getHistory(Long userId, Long conversationId);
}
