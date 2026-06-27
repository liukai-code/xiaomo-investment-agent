package com.itlk.myclaudecode.agent.service.Impl;

import com.itlk.myclaudecode.agent.service.TokenManager;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class TokenManagerImpl implements TokenManager {

    private static final String TOKEN_PREFIX = "auth:token:";
    private static final long TOKEN_EXPIRE_HOURS = 72;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String createToken(Long userId) {
        String oldToken = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + "user:" + userId);
        if (oldToken != null) {
            stringRedisTemplate.delete(TOKEN_PREFIX + oldToken);
        }

        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                TOKEN_PREFIX + token, String.valueOf(userId), TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.opsForValue().set(
                TOKEN_PREFIX + "user:" + userId, token, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        return token;
    }

    @Override
    public Long getUserId(String token) {
        String userId = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        return userId != null ? Long.valueOf(userId) : null;
    }

    @Override
    public void removeToken(String token) {
        String userId = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId != null) {
            stringRedisTemplate.delete(TOKEN_PREFIX + "user:" + userId);
        }
        stringRedisTemplate.delete(TOKEN_PREFIX + token);
    }
}
