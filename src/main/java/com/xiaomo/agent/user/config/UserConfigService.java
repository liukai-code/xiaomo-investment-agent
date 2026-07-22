package com.xiaomo.agent.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.common.entity.Result;
import com.xiaomo.agent.common.util.EncryptionService;
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

        // 从数据库获取激活渠道（兼容旧数据：is_active 为 NULL 时回退到第一条）
        UserConfig config = findEffectiveConfig(userId);
        if (config == null) {
            return null;
        }

        UserConfigDTO dto = convertToDTO(config);

        // 缓存到Redis
        cacheConfig(redisKey, dto);

        return dto;
    }

    @Transactional
    public void saveConfig(Long userId, UserConfigDTO dto) {
        UserConfig config = findEffectiveConfig(userId);

        if (config == null) {
            config = new UserConfig();
            config.setUserId(userId);
            config.setChannelName("默认渠道");
            config.setIsActive(true);
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
        // 删除激活渠道
        Optional<UserConfig> activeConfig = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeConfig.isPresent()) {
            userConfigRepository.deleteByIdAndUserId(activeConfig.get().getId(), userId);
        }
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
        UserConfig config = findEffectiveConfig(userId);
        if (config == null) {
            return null;
        }
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

    // ==================== 多渠道管理 ====================

    public ApiChannelListDTO listChannels(Long userId) {
        List<UserConfig> channels = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<ApiChannelDTO> dtoList = channels.stream()
                .map(this::convertToChannelDTO)
                .toList();

        Long activeChannelId = null;
        for (UserConfig ch : channels) {
            if (Boolean.TRUE.equals(ch.getIsActive())) {
                activeChannelId = ch.getId();
                break;
            }
        }

        return new ApiChannelListDTO(dtoList, activeChannelId);
    }

    public ApiChannelDTO getChannel(Long userId, Long channelId) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByIdAndUserId(channelId, userId);
        if (optionalConfig.isEmpty()) {
            return null;
        }
        return convertToChannelDTO(optionalConfig.get());
    }

    @Transactional
    public ApiChannelDTO createChannel(Long userId, ApiChannelDTO dto) {
        // 校验渠道名重复
        if (userConfigRepository.existsByUserIdAndChannelName(userId, dto.getChannelName())) {
            throw new IllegalArgumentException("渠道名称已存在");
        }

        UserConfig config = new UserConfig();
        config.setUserId(userId);
        config.setChannelName(dto.getChannelName());

        // 加密API Key
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            config.setApiKeyEncrypted(encryptionService.encrypt(dto.getApiKey()));
        }

        config.setBaseUrl(dto.getBaseUrl());
        config.setModelName(dto.getModelName());

        // 如果是用户的第一个渠道，自动激活
        List<UserConfig> existing = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (existing.isEmpty()) {
            config.setIsActive(true);
        } else {
            config.setIsActive(false);
        }

        userConfigRepository.save(config);
        clearCache(userId);

        return convertToChannelDTO(config);
    }

    @Transactional
    public ApiChannelDTO updateChannel(Long userId, Long channelId, ApiChannelDTO dto) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByIdAndUserId(channelId, userId);
        if (optionalConfig.isEmpty()) {
            throw new IllegalArgumentException("渠道不存在");
        }

        UserConfig config = optionalConfig.get();

        // 检查渠道名是否与其他渠道重复
        if (dto.getChannelName() != null && !dto.getChannelName().equals(config.getChannelName())) {
            if (userConfigRepository.existsByUserIdAndChannelName(userId, dto.getChannelName())) {
                throw new IllegalArgumentException("渠道名称已存在");
            }
            config.setChannelName(dto.getChannelName());
        }

        // 更新API Key（如果有新的）
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            config.setApiKeyEncrypted(encryptionService.encrypt(dto.getApiKey()));
        }

        config.setBaseUrl(dto.getBaseUrl());
        config.setModelName(dto.getModelName());

        userConfigRepository.save(config);
        clearCache(userId);

        return convertToChannelDTO(config);
    }

    @Transactional
    public void deleteChannel(Long userId, Long channelId) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByIdAndUserId(channelId, userId);
        if (optionalConfig.isEmpty()) {
            throw new IllegalArgumentException("渠道不存在");
        }

        boolean wasActive = Boolean.TRUE.equals(optionalConfig.get().getIsActive());
        userConfigRepository.deleteByIdAndUserId(channelId, userId);

        // 如果删除的是激活渠道，自动切换到下一个可用渠道
        if (wasActive) {
            List<UserConfig> remaining = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (!remaining.isEmpty()) {
                remaining.get(0).setIsActive(true);
                userConfigRepository.save(remaining.get(0));
            }
        }

        clearCache(userId);
    }

    @Transactional
    public void activateChannel(Long userId, Long channelId) {
        Optional<UserConfig> optionalConfig = userConfigRepository.findByIdAndUserId(channelId, userId);
        if (optionalConfig.isEmpty()) {
            throw new IllegalArgumentException("渠道不存在");
        }

        // 取消该用户所有渠道的激活状态
        userConfigRepository.deactivateAllByUserId(userId);

        // 激活目标渠道
        UserConfig config = optionalConfig.get();
        config.setIsActive(true);
        userConfigRepository.save(config);

        clearCache(userId);
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

    /**
     * 查找用户的有效配置：优先返回激活渠道，若无则回退到第一条（兼容旧数据）
     */
    private UserConfig findEffectiveConfig(Long userId) {
        Optional<UserConfig> active = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        if (active.isPresent()) {
            return active.get();
        }
        // 回退：旧数据 is_active 可能为 NULL，取第一条并标记为激活
        List<UserConfig> all = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (!all.isEmpty()) {
            UserConfig first = all.get(0);
            first.setIsActive(true);
            userConfigRepository.save(first);
            return first;
        }
        return null;
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

    private ApiChannelDTO convertToChannelDTO(UserConfig config) {
        ApiChannelDTO dto = new ApiChannelDTO();
        dto.setId(config.getId());
        dto.setChannelName(config.getChannelName());
        dto.setBaseUrl(config.getBaseUrl());
        dto.setModelName(config.getModelName());
        dto.setActive(Boolean.TRUE.equals(config.getIsActive()));
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());

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
