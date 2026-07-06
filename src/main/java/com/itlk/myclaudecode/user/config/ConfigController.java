package com.itlk.myclaudecode.user.config;

import com.itlk.myclaudecode.common.entity.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/config")
public class ConfigController {

    private final UserConfigService userConfigService;

    public ConfigController(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    @GetMapping
    public Result<UserConfigDTO> getConfig(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }

        UserConfigDTO config = userConfigService.getConfig(userId);
        return Result.success(config);
    }

    @PostMapping
    public Result<Void> saveConfig(HttpServletRequest request,
                                  @RequestBody UserConfigDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 验证API Key格式（基本检查）
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            if (dto.getApiKey().length() < 10) {
                return Result.error("API Key格式不正确");
            }
        }

        userConfigService.saveConfig(userId, dto);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> deleteConfig(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }

        userConfigService.deleteConfig(userId);
        return Result.success();
    }

    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(HttpServletRequest request,
                                                       @RequestBody UserConfigDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        return userConfigService.testConnection(dto);
    }
}
