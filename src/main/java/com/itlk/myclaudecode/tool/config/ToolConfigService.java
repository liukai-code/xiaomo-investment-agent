package com.itlk.myclaudecode.tool.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ToolConfigService {

    private static final String HASH_KEY = "agent:tool:config";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private volatile boolean initialized = false;

    public void initDefaults(List<String> toolNames) {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            HashOperations<String, String, String> ops = redisTemplate.opsForHash();
            Map<String, String> existing = ops.entries(HASH_KEY);
            if (existing.isEmpty()) {
                Map<String, String> defaults = new LinkedHashMap<>();
                for (String name : toolNames) {
                    defaults.put(name, "true");
                }
                ops.putAll(HASH_KEY, defaults);
                log.info("工具配置初始化完成，已注册 {} 个工具（默认全部启用）", toolNames.size());
            }
            initialized = true;
        }
    }

    public boolean isEnabled(String toolName) {
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        String val = ops.get(HASH_KEY, toolName);
        return !"false".equalsIgnoreCase(val);
    }

    public void setEnabled(String toolName, boolean enabled) {
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        ops.put(HASH_KEY, toolName, String.valueOf(enabled));
        log.info("工具 [{}] 已{}", toolName, enabled ? "启用" : "禁用");
    }

    public Map<String, Boolean> listAll() {
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        Map<String, String> entries = ops.entries(HASH_KEY);
        Map<String, Boolean> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(k, "true".equalsIgnoreCase(v)));
        return result;
    }
}
