package com.itlk.myclaudecode.agent.service;

import reactor.core.publisher.Flux;

public interface AgentLoop {

    String chat(Long userId, Long conversationId, String message);

    Flux<String> chatStream(Long userId, Long conversationId, String message);

    String generateTitle(Long userId, Long conversationId);
}
