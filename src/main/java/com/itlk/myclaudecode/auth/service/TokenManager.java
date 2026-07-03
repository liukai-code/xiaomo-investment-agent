package com.itlk.myclaudecode.auth.service;

public interface TokenManager {

    String createToken(Long userId);

    Long getUserId(String token);

    void refreshToken(String token, Long userId);

    void removeToken(String token);
}
