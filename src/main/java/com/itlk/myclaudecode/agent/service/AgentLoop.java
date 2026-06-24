package com.itlk.myclaudecode.agent.service;

import com.itlk.myclaudecode.agent.Entity.ChatMessage;
import com.itlk.myclaudecode.agent.Entity.Conversation;
import reactor.core.publisher.Flux;
import java.util.List;

public interface AgentLoop {

    Conversation createConversation(String title);

    List<Conversation> listConversations();

    List<ChatMessage> getHistory(Long conversationId);

    String chat(Long conversationId, String message);

    Flux<String> chatStream(Long conversationId, String message);
}
