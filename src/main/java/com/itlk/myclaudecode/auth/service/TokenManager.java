package com.itlk.myclaudecode.auth.service;

public interface TokenManager {

    String createToken(Long userId);

    Long getUserId(String token);

    void removeToken(String token);
}
