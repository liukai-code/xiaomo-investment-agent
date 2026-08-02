package com.xiaomo.agent.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.user.dto.UserPreferences;
import com.xiaomo.agent.user.entity.User;
import com.xiaomo.agent.user.repository.UserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserPreferencesCacheService {

    private static final String CACHE_PREFIX = "cache:user:prefs:";
    private static final long TTL_MINUTES = 30;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private UserRepository userRepository;

    /**
     * 获取用户偏好，优先走 Redis 缓存，缓存未命中则查 DB 并回填
     */
    public UserPreferences getPreferences(Long userId) {
        String key = CACHE_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("用户偏好缓存命中: userId={}", userId);
                return objectMapper.readValue(cached, UserPreferences.class);
            }
        } catch (Exception e) {
            log.warn("用户偏好缓存反序列化失败, userId={}: {}", userId, e.getMessage());
            stringRedisTemplate.delete(key);
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return null;
        }

        UserPreferences prefs = UserPreferences.fromEntity(userOpt.get());
        try {
            String json = objectMapper.writeValueAsString(prefs);
            stringRedisTemplate.opsForValue().set(key, json, TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("用户偏好已缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("用户偏好缓存序列化失败, userId={}: {}", userId, e.getMessage());
        }
        return prefs;
    }

    /**
     * 主动失效缓存，用户更新偏好后调用
     */
    public void evict(Long userId) {
        stringRedisTemplate.delete(CACHE_PREFIX + userId);
        log.debug("用户偏好缓存已失效: userId={}", userId);
    }
}
