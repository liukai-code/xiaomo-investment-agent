package com.itlk.myclaudecode.user.config;

import com.itlk.myclaudecode.common.util.EncryptionService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class UserConfigService {

    private static final String REDIS_KEY_PREFIX = "user:config:";
    private static final long CACHE_TTL_HOURS = 24;

    private final UserConfigRepository userConfigRepository;
    private final EncryptionService encryptionService;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserConfigService(UserConfigRepository userConfigRepository,
                           EncryptionService encryptionService,
                           RedisTemplate<String, Object> redisTemplate) {
        this.userConfigRepository = userConfigRepository;
        this.encryptionService = encryptionService;
        this.redisTemplate = redisTemplate;
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
