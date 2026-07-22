package com.xiaomo.agent.agent.service;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface AgentLoop {

    String chat(Long userId, Long conversationId, String message);

    Flux<ServerSentEvent<String>> chatStream(Long userId, Long conversationId, String message);

    String generateTitle(Long userId, Long conversationId);
}
