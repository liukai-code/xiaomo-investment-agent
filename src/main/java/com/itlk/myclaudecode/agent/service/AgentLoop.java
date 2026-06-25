package com.itlk.myclaudecode.agent.service;

import com.itlk.myclaudecode.agent.Entity.ChatMessage;
import com.itlk.myclaudecode.agent.Entity.Conversation;
import reactor.core.publisher.Flux;
import java.util.List;

public interface AgentLoop {

    Conversation createConversation(Long userId, String title);

    List<Conversation> listConversations(Long userId);

    List<ChatMessage> getHistory(Long userId, Long conversationId);

    String chat(Long userId, Long conversationId, String message);

    Flux<String> chatStream(Long userId, Long conversationId, String message);

    String generateTitle(Long userId, Long conversationId);
}
