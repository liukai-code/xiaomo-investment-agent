package com.itlk.myclaudecode.auth.controller;

import com.itlk.myclaudecode.auth.event.UserRegisteredEvent;
import com.itlk.myclaudecode.auth.service.TokenManager;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
import com.itlk.myclaudecode.user.service.AccountIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private UserRepository userRepository;

    @Resource
    private TokenManager tokenManager;

    @Resource
    private AccountIdGenerator accountIdGenerator;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || email.trim().isEmpty()) {
            return Result.error("邮箱不能为空");
        }
        if (!isValidEmail(email.trim())) {
            return Result.error("邮箱格式不正确");
        }
        if (password == null || password.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }
        if (userRepository.existsByEmail(email.trim())) {
            return Result.error("邮箱已被注册");
        }

        User user = new User();
        user.setEmail(email.trim());
        user.setAccountId(accountIdGenerator.generate());
        user.setPassword(passwordEncoder.encode(password));
        user.setFreeTokenQuota(100_000L);
        user.setFreeTokenUsed(0L);
        userRepository.save(user);

        // 异步发送注册欢迎通知
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));

        return Result.success(Map.of("id", user.getId(), "email", user.getEmail(), "accountId", user.getAccountId()));
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return Result.error("邮箱和密码不能为空");
        }

        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return Result.error("邮箱或密码错误");
        }

        String token = tokenManager.createToken(user.getId());
        return Result.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "email", user.getEmail(),
                "accountId", user.getAccountId()
        ));
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error("未登录");
        }
        String token = authHeader.substring(7);
        Long userId = tokenManager.getUserId(token);
        if (userId == null) {
            return Result.error("未登录");
        }
        tokenManager.removeToken(token);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error("未登录");
        }
        String token = authHeader.substring(7);
        Long userId = tokenManager.getUserId(token);
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("email", user.getEmail());
        data.put("accountId", user.getAccountId());
        data.put("freeTokenQuota", user.getFreeTokenQuota() != null ? user.getFreeTokenQuota() : 0L);
        data.put("freeTokenUsed", user.getFreeTokenUsed() != null ? user.getFreeTokenUsed() : 0L);
        data.put("createdAt", user.getCreatedAt());
        return Result.success(data);
    }

    @PostMapping("/changePassword")
    public Result<?> changePassword(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                    @RequestBody Map<String, String> body) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error("未登录");
        }
        String token = authHeader.substring(7);
        Long userId = tokenManager.getUserId(token);
        if (userId == null) {
            return Result.error("未登录");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || oldPassword.isEmpty()) {
            return Result.error("请输入旧密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return Result.error("新密码长度不能少于6位");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return Result.success();
    }

    private Long getUserIdFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return tokenManager.getUserId(authHeader.substring(7));
    }

    @GetMapping("/preferences")
    public Result<?> getPreferences(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = getUserIdFromToken(authHeader);
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("temperature", user.getTemperature());
        data.put("maxTokens", user.getMaxTokens());
        data.put("contextWindow", user.getContextWindow());
        data.put("memoryEnabled", user.getMemoryEnabled() != null ? user.getMemoryEnabled() : true);
        data.put("compressionEnabled", user.getCompressionEnabled() != null ? user.getCompressionEnabled() : true);
        return Result.success(data);
    }

    @PutMapping("/preferences")
    public Result<?> updatePreferences(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @RequestBody Map<String, Object> body) {
        Long userId = getUserIdFromToken(authHeader);
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (body.containsKey("temperature")) {
            double temp = ((Number) body.get("temperature")).doubleValue();
            if (temp < 0 || temp > 1) {
                return Result.error("temperature 必须在 0~1 之间");
            }
            user.setTemperature(temp);
        }
        if (body.containsKey("maxTokens")) {
            int tokens = ((Number) body.get("maxTokens")).intValue();
            if (tokens < 100 || tokens > 16384) {
                return Result.error("maxTokens 必须在 100~16384 之间");
            }
            user.setMaxTokens(tokens);
        }
        if (body.containsKey("contextWindow")) {
            int window = ((Number) body.get("contextWindow")).intValue();
            if (window < 5 || window > 100) {
                return Result.error("contextWindow 必须在 5~100 之间");
            }
            user.setContextWindow(window);
        }
        if (body.containsKey("memoryEnabled")) {
            user.setMemoryEnabled((Boolean) body.get("memoryEnabled"));
        }
        if (body.containsKey("compressionEnabled")) {
            user.setCompressionEnabled((Boolean) body.get("compressionEnabled"));
        }

        userRepository.save(user);
        return Result.success();
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
