package com.itlk.myclaudecode.agent.service;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class TokenManager {

    private static final String TOKEN_PREFIX = "auth:token:";
    private static final long TOKEN_EXPIRE_HOURS = 72;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public String createToken(Long userId) {
        // 删除该用户旧 token
        String oldToken = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + "user:" + userId);
        if (oldToken != null) {
            stringRedisTemplate.delete(TOKEN_PREFIX + oldToken);
        }

        String token = UUID.randomUUID().toString();
        // token -> userId
        stringRedisTemplate.opsForValue().set(
                TOKEN_PREFIX + token, String.valueOf(userId), TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        // userId -> token (用于反查删除旧 token)
        stringRedisTemplate.opsForValue().set(
                TOKEN_PREFIX + "user:" + userId, token, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        return token;
    }

    public Long getUserId(String token) {
        String userId = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        return userId != null ? Long.valueOf(userId) : null;
    }

    public void removeToken(String token) {
        String userId = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId != null) {
            stringRedisTemplate.delete(TOKEN_PREFIX + "user:" + userId);
        }
        stringRedisTemplate.delete(TOKEN_PREFIX + token);
    }
}
