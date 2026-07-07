package com.itlk.myclaudecode.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.common.config.HttpClientService;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.common.util.EncryptionService;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserConfigService {

    private static final String REDIS_KEY_PREFIX = "user:config:";
    private static final long CACHE_TTL_HOURS = 24;

    private final UserConfigRepository userConfigRepository;
    private final EncryptionService encryptionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    private final ToolCallingManager toolCallingManager;

    public UserConfigService(UserConfigRepository userConfigRepository,
                           EncryptionService encryptionService,
                           RedisTemplate<String, Object> redisTemplate,
                           HttpClientService httpClientService,
                           ObjectMapper objectMapper,
                           ToolCallingManager toolCallingManager) {
        this.userConfigRepository = userConfigRepository;
        this.encryptionService = encryptionService;
        this.redisTemplate = redisTemplate;
        this.httpClientService = httpClientService;
        this.objectMapper = objectMapper;
        this.toolCallingManager = toolCallingManager;
    }

    public UserConfigDTO getConfig(Long userId) {
        // 先尝试从Redis缓存获取
        String redisKey = REDIS_KEY_PREFIX + userId;
        UserConfigDTO cachedConfig = getCachedConfig(redisKey);
        if (cachedConfig != null) {
            return cachedConfig;
        }

        // 从数据库获取
        Optional<UserConfig> optionalConfig = userConfigRepository.findByUserId(userId);
        if (optionalConfig.isEmpty()) {
            return null;
        }

        UserConfig config = optionalConfig.get();
        UserConfigDTO dto = convertToDTO(config);

        // 缓存到Redis
        cacheConfig(redisKey, dto);

        return dto;
    }

    @Transactional
    public void saveConfig(Long userId, UserConfigDTO dto) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByUserId(userId);

        UserConfig config;
        if (optionalConfig.isPresent()) {
            config = optionalConfig.get();
        } else {
            config = new UserConfig();
            config.setUserId(userId);
        }

        // 加密API Key
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            String encryptedApiKey = encryptionService.encrypt(dto.getApiKey());
            config.setApiKeyEncrypted(encryptedApiKey);
        }

        config.setBaseUrl(dto.getBaseUrl());
        config.setModelName(dto.getModelName());

        userConfigRepository.save(config);

        // 清除缓存
        clearCache(userId);
    }

    @Transactional
    public void deleteConfig(Long userId) {
        userConfigRepository.deleteByUserId(userId);
        clearCache(userId);
    }

    public String getDecryptedApiKey(Long userId) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByUserId(userId);
        if (optionalConfig.isEmpty() || optionalConfig.get().getApiKeyEncrypted() == null) {
            return null;
        }
        return encryptionService.decrypt(optionalConfig.get().getApiKeyEncrypted());
    }

    /**
     * 基于用户配置创建 ChatModel，用于 Per-User API Key 路由。
     * 若用户无自定义配置，返回 null（调用方应使用全局默认 ChatModel）。
     */
    public ChatModel getUserChatModel(Long userId) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByUserId(userId);
        if (optionalConfig.isEmpty()) {
            return null;
        }

        UserConfig config = optionalConfig.get();
        if (config.getApiKeyEncrypted() == null || config.getApiKeyEncrypted().isEmpty()) {
            return null;
        }

        String apiKey = encryptionService.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.anthropic.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String modelName = config.getModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = "claude-sonnet-4-20250514";
        }

        try {
            AnthropicApi anthropicApi = AnthropicApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();
            return AnthropicChatModel.builder()
                    .anthropicApi(anthropicApi)
                    .toolCallingManager(toolCallingManager)
                    .defaultOptions(org.springframework.ai.anthropic.AnthropicChatOptions.builder()
                            .model(modelName)
                            .maxTokens(4096)
                            .temperature(0.7)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("创建用户级 ChatModel 失败, userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    public Result<Map<String, Object>> testConnection(UserConfigDTO dto) {
        if (dto.getApiKey() == null || dto.getApiKey().isEmpty()) {
            return Result.error("API Key 不能为空");
        }

        String apiKey = dto.getApiKey();

        String baseUrl = dto.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.anthropic.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String modelName = dto.getModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = "claude-3-sonnet-20240229";
        }

        String url = baseUrl + "/v1/messages";
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", modelName,
                    "max_tokens", 1,
                    "messages", List.of(Map.of("role", "user", "content", "hi"))
            ));
        } catch (Exception e) {
            return Result.error("请求构建失败");
        }

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            long startTime = System.currentTimeMillis();
            String responseBody = httpClientService.execute(request);
            long latency = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("latencyMs", latency);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                if (responseMap.containsKey("model")) {
                    result.put("model", responseMap.get("model"));
                }
            } catch (Exception ignored) {
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error(parseConnectionError(e));
        }
    }

    private String parseConnectionError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.toString();
        if (msg.contains("401") || msg.contains("Unauthorized")) return "API Key 无效或已过期";
        if (msg.contains("403") || msg.contains("Forbidden")) return "API Key 权限不足";
        if (msg.contains("404") || msg.contains("Not Found")) return "API 端点不存在，请检查 Base URL";
        if (msg.contains("429") || msg.contains("Too Many Requests")) return "请求过于频繁，请稍后重试";
        if (msg.contains("ConnectException")) return "无法连接到服务器，请检查 Base URL 和网络";
        if (msg.contains("SocketTimeoutException")) return "连接超时，请检查网络";
        if (msg.contains("UnknownHostException")) return "无法解析域名，请检查 Base URL";
        return "连接失败: " + msg;
    }

    private UserConfigDTO getCachedConfig(String redisKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached instanceof UserConfigDTO) {
                return (UserConfigDTO) cached;
            }
        } catch (Exception e) {
            // 缓存读取失败，忽略
        }
        return null;
    }

    private void cacheConfig(String redisKey, UserConfigDTO dto) {
        try {
            redisTemplate.opsForValue().set(redisKey, dto, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // 缓存写入失败，忽略
        }
    }

    private void clearCache(Long userId) {
        try {
            String redisKey = REDIS_KEY_PREFIX + userId;
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            // 缓存清除失败，忽略
        }
    }

    private UserConfigDTO convertToDTO(UserConfig config) {
        UserConfigDTO dto = new UserConfigDTO();
        dto.setBaseUrl(config.getBaseUrl());
        dto.setModelName(config.getModelName());

        // 脱敏显示API Key
        if (config.getApiKeyEncrypted() != null) {
            try {
                String decrypted = encryptionService.decrypt(config.getApiKeyEncrypted());
                dto.setApiKey(maskApiKey(decrypted));
            } catch (Exception e) {
                dto.setApiKey("***");
            }
        }

        return dto;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 6) {
            return "***";
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }
}
