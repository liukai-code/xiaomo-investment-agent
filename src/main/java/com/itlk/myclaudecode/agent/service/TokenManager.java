package com.itlk.myclaudecode.agent.service;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenManager {

    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    public String createToken(Long userId) {
        tokenStore.values().removeIf(v -> v.equals(userId));
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, userId);
        return token;
    }

    public Long getUserId(String token) {
        return tokenStore.get(token);
    }

    public void removeToken(String token) {
        tokenStore.remove(token);
    }
}
