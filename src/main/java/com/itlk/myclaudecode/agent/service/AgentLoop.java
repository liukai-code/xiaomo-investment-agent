package com.itlk.myclaudecode.agent.service;

import reactor.core.publisher.Flux;

public interface AgentLoop {
    String chat(String message);
    Flux<String> chatStream(String message);
}
