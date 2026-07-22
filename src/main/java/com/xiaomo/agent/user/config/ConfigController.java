package com.xiaomo.agent.user.config;

import com.xiaomo.agent.common.entity.Result;
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

    // ==================== 多渠道管理 ====================

    @GetMapping("/channels")
    public Result<ApiChannelListDTO> listChannels(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        return Result.success(userConfigService.listChannels(userId));
    }

    @GetMapping("/channels/{channelId}")
    public Result<ApiChannelDTO> getChannel(HttpServletRequest request,
                                             @PathVariable Long channelId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        ApiChannelDTO channel = userConfigService.getChannel(userId, channelId);
        if (channel == null) {
            return Result.error("渠道不存在");
        }
        return Result.success(channel);
    }

    @PostMapping("/channels")
    public Result<ApiChannelDTO> createChannel(HttpServletRequest request,
                                                @RequestBody ApiChannelDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        if (dto.getChannelName() == null || dto.getChannelName().trim().isEmpty()) {
            return Result.error("渠道名称不能为空");
        }
        if (dto.getApiKey() == null || dto.getApiKey().length() < 10) {
            return Result.error("API Key格式不正确");
        }
        try {
            return Result.success(userConfigService.createChannel(userId, dto));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/channels/{channelId}")
    public Result<ApiChannelDTO> updateChannel(HttpServletRequest request,
                                                @PathVariable Long channelId,
                                                @RequestBody ApiChannelDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        try {
            return Result.success(userConfigService.updateChannel(userId, channelId, dto));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/channels/{channelId}")
    public Result<Void> deleteChannel(HttpServletRequest request,
                                       @PathVariable Long channelId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        try {
            userConfigService.deleteChannel(userId, channelId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/channels/{channelId}/activate")
    public Result<Void> activateChannel(HttpServletRequest request,
                                         @PathVariable Long channelId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        try {
            userConfigService.activateChannel(userId, channelId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
