package com.itlk.myclaudecode.agent.service;

public interface TokenManager {

    String createToken(Long userId);

    Long getUserId(String token);

    void removeToken(String token);
}
