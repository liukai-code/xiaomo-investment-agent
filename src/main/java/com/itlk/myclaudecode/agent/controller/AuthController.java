package com.itlk.myclaudecode.agent.controller;

import com.itlk.myclaudecode.agent.Entity.Result;
import com.itlk.myclaudecode.agent.Entity.User;
import com.itlk.myclaudecode.agent.repository.UserRepository;
import com.itlk.myclaudecode.agent.service.TokenManager;
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

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }
        if (userRepository.existsByUsername(username.trim())) {
            return Result.error("用户名已存在");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        return Result.success(Map.of("id", user.getId(), "username", user.getUsername()));
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return Result.error("用户名和密码不能为空");
        }

        User user = userRepository.findByUsername(username.trim()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        String token = tokenManager.createToken(user.getId());
        return Result.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername()
        ));
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestAttribute Long userId, @RequestAttribute String token) {
        tokenManager.removeToken(token);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<?> me(@RequestAttribute Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(Map.of("id", user.getId(), "username", user.getUsername()));
    }
}
