package com.itlk.myclaudecode.notification.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Resource
    private UserRepository userRepository;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(user -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", user.getId());
                    map.put("email", user.getEmail());
                    map.put("accountId", user.getAccountId());
                    return map;
                })
                .toList();
        return Result.success(users);
    }
}
