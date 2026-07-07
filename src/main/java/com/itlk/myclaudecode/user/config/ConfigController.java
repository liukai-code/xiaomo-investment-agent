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

        // 验证API Key格式
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            String apiKey = dto.getApiKey();
            if (apiKey.length() < 10) {
                return Result.error("API Key格式不正确，长度不能少于10个字符");
            }
            // 根据 Base URL 判断 provider 类型并校验格式
            String baseUrl = dto.getBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                if (baseUrl.contains("openai.com") && !apiKey.startsWith("sk-")) {
                    return Result.error("OpenAI API Key 通常以 'sk-' 开头，请检查是否正确");
                }
            } else {
                // 默认 Anthropic，校验常见格式
                if (!apiKey.startsWith("sk-ant-") && !apiKey.startsWith("sk-") && apiKey.length() < 20) {
                    return Result.error("API Key格式不正确，请确认是否为有效的 Anthropic API Key");
                }
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
