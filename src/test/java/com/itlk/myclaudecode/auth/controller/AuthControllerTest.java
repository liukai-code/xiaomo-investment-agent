package com.itlk.myclaudecode.auth.controller;

import com.itlk.myclaudecode.auth.service.TokenManager;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
import com.itlk.myclaudecode.user.service.AccountIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 认证接口测试")
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private AccountIdGenerator accountIdGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthController authController;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // ========== register 注册 ==========

    @Nested
    @DisplayName("register 用户注册")
    class RegisterTest {

        @Test
        @DisplayName("正常注册 → 返回用户信息")
        void success() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(accountIdGenerator.generate()).thenReturn("user_123456");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            Result<?> result = authController.register(Map.of(
                    "email", "test@example.com",
                    "password", "123456"
            ));

            assertEquals(1, result.getCode(), "注册应成功");
            assertNotNull(result.getData(), "应返回数据");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("空邮箱 → 返回错误")
        void emptyEmail() {
            Result<?> result = authController.register(Map.of(
                    "email", "",
                    "password", "123456"
            ));
            assertEquals(0, result.getCode(), "空邮箱应失败");
            assertTrue(result.getMsg().contains("邮箱"), "错误信息应提及邮箱");
        }

        @Test
        @DisplayName("null 邮箱 → 返回错误")
        void nullEmail() {
            Result<?> result = authController.register(Map.of(
                    "password", "123456"
            ));
            assertEquals(0, result.getCode(), "null邮箱应失败");
        }

        @Test
        @DisplayName("邮箱格式不正确 → 返回错误")
        void invalidEmail() {
            Result<?> result = authController.register(Map.of(
                    "email", "not-an-email",
                    "password", "123456"
            ));
            assertEquals(0, result.getCode(), "格式错误的邮箱应失败");
            assertTrue(result.getMsg().contains("格式"), "错误信息应提及格式");
        }

        @Test
        @DisplayName("密码不足6位 → 返回错误")
        void shortPassword() {
            Result<?> result = authController.register(Map.of(
                    "email", "test@example.com",
                    "password", "12345"
            ));
            assertEquals(0, result.getCode(), "短密码应失败");
            assertTrue(result.getMsg().contains("6"), "错误信息应提及6位");
        }

        @Test
        @DisplayName("邮箱已被注册 → 返回错误")
        void duplicateEmail() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            Result<?> result = authController.register(Map.of(
                    "email", "test@example.com",
                    "password", "123456"
            ));
            assertEquals(0, result.getCode(), "重复邮箱应失败");
            assertTrue(result.getMsg().contains("已被注册"), "错误信息应提示已注册");
        }
    }

    // ========== login 登录 ==========

    @Nested
    @DisplayName("login 用户登录")
    class LoginTest {

        @Test
        @DisplayName("正常登录 → 返回 token")
        void success() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setAccountId("user_123456");
            user.setPassword(ENCODER.encode("123456"));

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(tokenManager.createToken(1L)).thenReturn("test-token-uuid");

            Result<?> result = authController.login(Map.of(
                    "email", "test@example.com",
                    "password", "123456"
            ));

            assertEquals(1, result.getCode(), "登录应成功");
            Map<?, ?> data = (Map<?, ?>) result.getData();
            assertEquals("test-token-uuid", data.get("token"), "应返回token");
            assertEquals(1L, data.get("userId"), "应返回userId");
        }

        @Test
        @DisplayName("用户不存在 → 返回错误")
        void userNotFound() {
            when(userRepository.findByEmail("noone@example.com")).thenReturn(Optional.empty());

            Result<?> result = authController.login(Map.of(
                    "email", "noone@example.com",
                    "password", "123456"
            ));
            assertEquals(0, result.getCode(), "不存在的用户应失败");
            assertTrue(result.getMsg().contains("错误"), "错误信息应提示邮箱或密码错误");
        }

        @Test
        @DisplayName("密码错误 → 返回错误")
        void wrongPassword() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setPassword(ENCODER.encode("correct-password"));

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            Result<?> result = authController.login(Map.of(
                    "email", "test@example.com",
                    "password", "wrong-password"
            ));
            assertEquals(0, result.getCode(), "错误密码应失败");
            assertTrue(result.getMsg().contains("错误"), "错误信息应提示邮箱或密码错误");
        }

        @Test
        @DisplayName("空邮箱或密码 → 返回错误")
        void nullFields() {
            Result<?> r1 = authController.login(Map.of("password", "123456"));
            assertEquals(0, r1.getCode(), "null邮箱应失败");

            Result<?> r2 = authController.login(Map.of("email", "test@example.com"));
            assertEquals(0, r2.getCode(), "null密码应失败");
        }
    }

    // ========== logout 登出 ==========

    @Nested
    @DisplayName("logout 用户登出")
    class LogoutTest {

        @Test
        @DisplayName("正常登出 → 返回成功")
        void success() {
            when(tokenManager.getUserId("valid-token")).thenReturn(1L);

            Result<?> result = authController.logout("Bearer valid-token");
            assertEquals(1, result.getCode(), "登出应成功");
            verify(tokenManager).removeToken("valid-token");
        }

        @Test
        @DisplayName("无 Authorization header → 返回未登录")
        void noHeader() {
            Result<?> result = authController.logout(null);
            assertEquals(0, result.getCode(), "无header应失败");
            assertTrue(result.getMsg().contains("未登录"));
        }

        @Test
        @DisplayName("非 Bearer 格式 → 返回未登录")
        void invalidHeaderFormat() {
            Result<?> result = authController.logout("Basic abc123");
            assertEquals(0, result.getCode(), "非Bearer格式应失败");
        }

        @Test
        @DisplayName("token 无效 → 返回未登录")
        void invalidToken() {
            when(tokenManager.getUserId("invalid-token")).thenReturn(null);

            Result<?> result = authController.logout("Bearer invalid-token");
            assertEquals(0, result.getCode(), "无效token应失败");
        }
    }

    // ========== me 查询当前用户 ==========

    @Nested
    @DisplayName("me 查询当前用户")
    class MeTest {

        @Test
        @DisplayName("正常查询 → 返回用户信息")
        void success() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setAccountId("user_123456");

            when(tokenManager.getUserId("valid-token")).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            Result<?> result = authController.me("Bearer valid-token");
            assertEquals(1, result.getCode(), "查询应成功");
            Map<?, ?> data = (Map<?, ?>) result.getData();
            assertEquals(1L, data.get("id"));
            assertEquals("test@example.com", data.get("email"));
        }

        @Test
        @DisplayName("无 Authorization header → 返回未登录")
        void noHeader() {
            Result<?> result = authController.me(null);
            assertEquals(0, result.getCode(), "无header应失败");
        }

        @Test
        @DisplayName("token 无效 → 返回未登录")
        void invalidToken() {
            when(tokenManager.getUserId("bad-token")).thenReturn(null);

            Result<?> result = authController.me("Bearer bad-token");
            assertEquals(0, result.getCode(), "无效token应失败");
        }

        @Test
        @DisplayName("用户不存在 → 返回错误")
        void userDeleted() {
            when(tokenManager.getUserId("valid-token")).thenReturn(999L);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            Result<?> result = authController.me("Bearer valid-token");
            assertEquals(0, result.getCode(), "用户不存在应失败");
            assertTrue(result.getMsg().contains("不存在"));
        }
    }
}
