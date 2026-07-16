package com.itlk.myclaudecode.auth.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenManagerImpl Token管理测试")
class TokenManagerImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenManagerImpl tokenManager;

    // ========== createToken ==========

    @Nested
    @DisplayName("createToken 创建Token")
    class CreateTokenTest {

        @Test
        @DisplayName("首次创建 → 生成UUID并存储双向映射")
        void firstToken() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:token:user:1")).thenReturn(null);

            String token = tokenManager.createToken(1L);

            assertNotNull(token, "token不应为null");
            assertFalse(token.isEmpty(), "token不应为空");
            // 验证存储了 token→userId
            verify(valueOps).set(eq("auth:token:" + token), eq("1"), eq(72L), eq(TimeUnit.HOURS));
            // 验证存储了 userId→token
            verify(valueOps).set(eq("auth:token:user:1"), eq(token), eq(72L), eq(TimeUnit.HOURS));
        }

        @Test
        @DisplayName("已有旧token → 先删除旧token再创建新的")
        void replaceOldToken() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:token:user:1")).thenReturn("old-token-uuid");

            String token = tokenManager.createToken(1L);

            assertNotNull(token, "新token不应为null");
            // 验证删除了旧token
            verify(stringRedisTemplate).delete("auth:token:old-token-uuid");
        }
    }

    // ========== getUserId ==========

    @Nested
    @DisplayName("getUserId 查询Token对应的用户")
    class GetUserIdTest {

        @Test
        @DisplayName("token存在 → 返回userId")
        void tokenExists() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:token:valid-token")).thenReturn("42");

            Long userId = tokenManager.getUserId("valid-token");
            assertEquals(42L, userId, "应返回正确的userId");
        }

        @Test
        @DisplayName("token不存在 → 返回null")
        void tokenNotExists() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:token:invalid-token")).thenReturn(null);

            Long userId = tokenManager.getUserId("invalid-token");
            assertNull(userId, "不存在的token应返回null");
        }
    }

    // ========== removeToken ==========

    @Nested
    @DisplayName("removeToken 删除Token")
    class RemoveTokenTest {

        @Test
        @DisplayName("token存在 → 先查userId再删除双向映射")
        void removeExisting() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:token:valid-token")).thenReturn("1");

            tokenManager.removeToken("valid-token");

            // 验证先查询了 token→userId 的映射
            verify(valueOps).get("auth:token:valid-token");
            // 验证删除了 userId→token
            verify(stringRedisTemplate).delete("auth:token:user:1");
            // 验证删除了 token→userId
            verify(stringRedisTemplate).delete("auth:token:valid-token");
        }

        @Test
        @DisplayName("token不存在 → 静默处理不报错")
        void removeNonExisting() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:token:ghost")).thenReturn(null);

            assertDoesNotThrow(() -> tokenManager.removeToken("ghost"),
                    "删除不存在的token不应抛异常");
            // 仍应尝试删除 token 本身
            verify(stringRedisTemplate).delete("auth:token:ghost");
        }
    }

    // ========== refreshToken ==========

    @Nested
    @DisplayName("refreshToken 续期Token")
    class RefreshTokenTest {

        @Test
        @DisplayName("续期 → 两个key都延长TTL")
        void refreshBothKeys() {
            when(stringRedisTemplate.expire(anyString(), eq(72L), eq(TimeUnit.HOURS)))
                    .thenReturn(true);

            tokenManager.refreshToken("valid-token", 1L);

            verify(stringRedisTemplate).expire("auth:token:valid-token", 72L, TimeUnit.HOURS);
            verify(stringRedisTemplate).expire("auth:token:user:1", 72L, TimeUnit.HOURS);
        }
    }
}
