package com.itlk.myclaudecode.user.config;

import com.itlk.myclaudecode.common.Result;
import com.itlk.myclaudecode.user.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/config")
public class ConfigController {

    private final UserConfigService userConfigService;
    private final UserService userService;

    public ConfigController(UserConfigService userConfigService, UserService userService) {
        this.userConfigService = userConfigService;
        this.userService = userService;
    }

    @GetMapping
    public Result<UserConfigDTO> getConfig(@RequestHeader("Authorization") String token) {
        Long userId = userService.getCurrentUserId(token);
        if (userId == null) {
            return Result.error("用户未登录");
        }

        UserConfigDTO config = userConfigService.getConfig(userId);
        return Result.success(config);
    }

    @PostMapping
    public Result<Void> saveConfig(@RequestHeader("Authorization") String token,
                                  @RequestBody UserConfigDTO dto) {
        Long userId = userService.getCurrentUserId(token);
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
    public Result<Void> deleteConfig(@RequestHeader("Authorization") String token) {
        Long userId = userService.getCurrentUserId(token);
        if (userId == null) {
            return Result.error("用户未登录");
        }

        userConfigService.deleteConfig(userId);
        return Result.success();
    }
}
