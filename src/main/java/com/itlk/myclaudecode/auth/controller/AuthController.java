package com.itlk.myclaudecode.auth.controller;

import com.itlk.myclaudecode.auth.service.TokenManager;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
import com.itlk.myclaudecode.user.service.AccountIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
        userRepository.save(user);

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
        return Result.success(Map.of("id", user.getId(), "email", user.getEmail(), "accountId", user.getAccountId()));
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
