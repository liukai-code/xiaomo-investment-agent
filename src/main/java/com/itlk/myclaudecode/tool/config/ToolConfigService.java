package com.itlk.myclaudecode.tool.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            Set<String> registeredNames = new HashSet<>(toolNames);

            if (existing.isEmpty()) {
                // 首次初始化：写入全部工具，默认启用
                Map<String, String> defaults = new LinkedHashMap<>();
                for (String name : toolNames) {
                    defaults.put(name, "true");
                }
                ops.putAll(HASH_KEY, defaults);
                log.info("工具配置初始化完成，已注册 {} 个工具（默认全部启用）", toolNames.size());
            } else {
                // 清理已不存在的旧工具名
                List<String> staleKeys = existing.keySet().stream()
                        .filter(k -> !registeredNames.contains(k))
                        .toList();
                if (!staleKeys.isEmpty()) {
                    ops.delete(HASH_KEY, staleKeys.toArray());
                    log.info("已清理 {} 个过期工具配置: {}", staleKeys.size(), staleKeys);
                }
                // 补充新增的工具（默认启用）
                for (String name : toolNames) {
                    if (!existing.containsKey(name)) {
                        ops.put(HASH_KEY, name, "true");
                        log.info("新增工具配置: {}（默认启用）", name);
                    }
                }
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
