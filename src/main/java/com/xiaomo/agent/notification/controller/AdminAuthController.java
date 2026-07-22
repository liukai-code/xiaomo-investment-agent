package com.xiaomo.agent.notification.controller;

import com.xiaomo.agent.common.entity.Result;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    @Value("${admin.password}")
    private String adminPassword;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String ADMIN_TOKEN_PREFIX = "auth:admin:token:";

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || !password.equals(adminPassword)) {
            return Result.error("密码错误");
        }

        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                ADMIN_TOKEN_PREFIX + token, "admin", 2, TimeUnit.HOURS);

        return Result.success(Map.of("token", token));
    }
}
