package com.xiaomo.agent.tool.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolConfigService 工具配置测试")
class ToolConfigServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @SuppressWarnings("unchecked")
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private ToolConfigService toolConfigService;

    @BeforeEach
    void setUp() throws Exception {
        toolConfigService = new ToolConfigService();

        // 通过反射注入 redisTemplate（因为 @Resource 注入）
        Field redisField = ToolConfigService.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(toolConfigService, redisTemplate);

        // 重置 initialized 标志，确保每个测试独立
        Field initField = ToolConfigService.class.getDeclaredField("initialized");
        initField.setAccessible(true);
        initField.set(toolConfigService, false);
    }

    // ========== initDefaults ==========

    @Nested
    @DisplayName("initDefaults 初始化默认配置")
    class InitDefaultsTest {

        @Test
        @DisplayName("首次初始化（Redis为空）→ 写入全部工具默认启用")
        void firstInitEmptyRedis() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            when(hashOps.entries("agent:tool:config")).thenReturn(new LinkedHashMap<>());

            toolConfigService.initDefaults(List.of("toolA", "toolB", "toolC"));

            verify(hashOps).putAll(eq("agent:tool:config"), argThat(map -> {
                Map<?, ?> m = (Map<?, ?>) map;
                return m.size() == 3
                        && "true".equals(m.get("toolA"))
                        && "true".equals(m.get("toolB"))
                        && "true".equals(m.get("toolC"));
            }));
        }

        @Test
        @DisplayName("已有数据 → 清理过期工具 + 补充新工具")
        void existingDataMerge() {
            Map<String, String> existing = new LinkedHashMap<>();
            existing.put("toolA", "true");
            existing.put("oldTool", "false");

            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            when(hashOps.entries("agent:tool:config")).thenReturn(new LinkedHashMap<>(existing));

            toolConfigService.initDefaults(List.of("toolA", "toolB"));

            // 应删除 oldTool
            verify(hashOps).delete("agent:tool:config", new Object[]{"oldTool"});
            // 应补充 toolB
            verify(hashOps).put("agent:tool:config", "toolB", "true");
            // 不应重复写入 toolA
            verify(hashOps, never()).put("agent:tool:config", "toolA", "true");
        }

        @Test
        @DisplayName("initialized=true 后再次调用 → 跳过执行")
        void skipAfterInitialized() throws Exception {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            when(hashOps.entries("agent:tool:config")).thenReturn(new LinkedHashMap<>());

            toolConfigService.initDefaults(List.of("toolA"));
            // 第二次调用
            toolConfigService.initDefaults(List.of("toolB"));

            // entries 只应被调用一次
            verify(hashOps, times(1)).entries("agent:tool:config");
        }
    }

    // ========== isEnabled ==========

    @Nested
    @DisplayName("isEnabled 检查工具是否启用")
    class IsEnabledTest {

        @Test
        @DisplayName("工具启用 → 返回true")
        void enabled() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            doReturn("true").when(hashOps).get("agent:tool:config", "toolA");

            assertTrue(toolConfigService.isEnabled("toolA"), "应返回true");
        }

        @Test
        @DisplayName("工具禁用 → 返回false")
        void disabled() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            doReturn("false").when(hashOps).get("agent:tool:config", "toolA");

            assertFalse(toolConfigService.isEnabled("toolA"), "应返回false");
        }

        @Test
        @DisplayName("key不存在 → 默认返回true")
        void keyMissing() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            doReturn(null).when(hashOps).get("agent:tool:config", "newTool");

            assertTrue(toolConfigService.isEnabled("newTool"), "不存在的工具应默认启用");
        }

        @Test
        @DisplayName("大小写FALSE → 返回false")
        void caseInsensitiveFalse() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            doReturn("FALSE").when(hashOps).get("agent:tool:config", "toolA");

            assertFalse(toolConfigService.isEnabled("toolA"), "大小写FALSE应返回false");
        }
    }

    // ========== setEnabled ==========

    @Nested
    @DisplayName("setEnabled 设置工具开关")
    class SetEnabledTest {

        @Test
        @DisplayName("启用工具 → 写入true")
        void enable() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);

            toolConfigService.setEnabled("toolA", true);

            verify(hashOps).put("agent:tool:config", "toolA", "true");
        }

        @Test
        @DisplayName("禁用工具 → 写入false")
        void disable() {
            when(redisTemplate.opsForHash()).thenReturn(hashOps);

            toolConfigService.setEnabled("toolA", false);

            verify(hashOps).put("agent:tool:config", "toolA", "false");
        }
    }

    // ========== listAll ==========

    @Nested
    @DisplayName("listAll 列出全部配置")
    class ListAllTest {

        @Test
        @DisplayName("返回完整配置map")
        void returnAll() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("toolA", "true");
            entries.put("toolB", "false");

            when(redisTemplate.opsForHash()).thenReturn(hashOps);
            when(hashOps.entries("agent:tool:config")).thenReturn(new LinkedHashMap<>(entries));

            Map<String, Boolean> result = toolConfigService.listAll();

            assertEquals(2, result.size(), "应返回2个工具配置");
            assertTrue(result.get("toolA"), "toolA应为true");
            assertFalse(result.get("toolB"), "toolB应为false");
        }
    }
}
