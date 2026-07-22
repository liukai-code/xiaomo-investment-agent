package com.xiaomo.agent.yjb.service;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class YjbTokenStore {

    private static final String KEY_PREFIX = "yjb:token:";
    private static final long TTL_DAYS = 30;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void saveToken(Long userId, String yjbToken) {
        stringRedisTemplate.opsForValue().set(
                KEY_PREFIX + userId, yjbToken, TTL_DAYS, TimeUnit.DAYS);
    }

    public String getToken(Long userId) {
        return stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
    }

    public void removeToken(Long userId) {
        stringRedisTemplate.delete(KEY_PREFIX + userId);
    }

    public boolean hasToken(Long userId) {
        Boolean exists = stringRedisTemplate.hasKey(KEY_PREFIX + userId);
        return Boolean.TRUE.equals(exists);
    }
}
