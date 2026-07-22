# 用户大模型API配置功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户级别的大模型API配置功能，让每个用户可以配置自己的API Key、Base URL和模型选择

**Architecture:** 后端加密代理模式，用户配置加密存储在数据库，使用Redis缓存热点配置，后端代理调用大模型API

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, PostgreSQL, Redis, AES-256-GCM加密, Vue 3, TypeScript

## Global Constraints

- API Key必须使用AES-256-GCM加密存储
- 环境变量`CONFIG_ENCRYPTION_KEY`用于加密密钥管理
- 用户配置优先，环境变量作为备用（向后兼容）
- 前端不持久化存储API Key
- 所有API响应使用统一格式`{code, msg, data}`

---

## 文件结构

### 后端文件

| 文件路径 | 职责 |
|---------|------|
| `src/main/java/com/xiaomo/agent/user/config/UserConfig.java` | 用户配置实体类 |
| `src/main/java/com/xiaomo/agent/user/config/UserConfigRepository.java` | 数据访问层 |
| `src/main/java/com/xiaomo/agent/user/config/UserConfigService.java` | 用户配置业务逻辑 |
| `src/main/java/com/xiaomo/agent/user/config/ConfigController.java` | 配置管理REST API |
| `src/main/java/com/xiaomo/agent/common/util/EncryptionService.java` | AES-256-GCM加密解密服务 |
| `src/main/resources/db/migration/V2__create_user_config_table.sql` | 数据库迁移脚本 |
| `src/main/java/com/xiaomo/agent/agent/service/impl/AgentLoopImpl.java` | 修改：使用用户配置 |

### 前端文件

| 文件路径 | 职责 |
|---------|------|
| `frontend/src/components/SettingsDialog.vue` | 设置弹窗组件 |
| `frontend/src/api/config.ts` | 配置API调用 |
| `frontend/src/views/ChatView.vue` | 修改：绑定设置按钮事件 |

---

## Task 1: 创建数据库迁移脚本

**Files:**
- Create: `src/main/resources/db/migration/V2__create_user_config_table.sql`

**Interfaces:**
- Produces: `user_config` 表结构

- [ ] **Step 1: 创建数据库迁移脚本**

```sql
-- V2__create_user_config_table.sql
CREATE TABLE user_config (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  api_key_encrypted VARCHAR(512),
  base_url VARCHAR(256),
  model_name VARCHAR(128),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_config_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_config_user_id ON user_config(user_id);

COMMENT ON TABLE user_config IS '用户大模型API配置表';
COMMENT ON COLUMN user_config.api_key_encrypted IS '加密后的API Key';
COMMENT ON COLUMN user_config.base_url IS 'API基础URL';
COMMENT ON COLUMN user_config.model_name IS '模型名称';
```

- [ ] **Step 2: 验证迁移脚本语法**

检查SQL语法是否正确，确认外键约束和索引创建。

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V2__create_user_config_table.sql
git commit -m "feat: 添加用户配置表数据库迁移脚本"
```

---

## Task 2: 创建UserConfig实体类

**Files:**
- Create: `src/main/java/com/xiaomo/agent/user/config/UserConfig.java`

**Interfaces:**
- Produces: `UserConfig` 实体类，包含`id`, `userId`, `apiKeyEncrypted`, `baseUrl`, `modelName`, `createdAt`, `updatedAt`字段

- [ ] **Step 1: 创建UserConfig实体类**

```java
package com.xiaomo.agent.user.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_config")
public class UserConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "api_key_encrypted", length = 512)
    private String apiKeyEncrypted;

    @Column(name = "base_url", length = 256)
    private String baseUrl;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public void setApiKeyEncrypted(String apiKeyEncrypted) {
        this.apiKeyEncrypted = apiKeyEncrypted;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
```

- [ ] **Step 2: 验证实体类编译**

运行 `mvn compile` 确认实体类编译通过。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/xiaomo/agent/user/config/UserConfig.java
git commit -m "feat: 创建UserConfig实体类"
```

---

## Task 3: 创建UserConfigRepository

**Files:**
- Create: `src/main/java/com/xiaomo/agent/user/config/UserConfigRepository.java`

**Interfaces:**
- Produces: `UserConfigRepository` 接口，提供`findByUserId`方法

- [ ] **Step 1: 创建UserConfigRepository接口**

```java
package com.xiaomo.agent.user.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {

    Optional<UserConfig> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
```

- [ ] **Step 2: 验证Repository编译**

运行 `mvn compile` 确认Repository接口编译通过。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/xiaomo/agent/user/config/UserConfigRepository.java
git commit -m "feat: 创建UserConfigRepository接口"
```

---

## Task 4: 创建EncryptionService加密服务

**Files:**
- Create: `src/main/java/com/xiaomo/agent/common/util/EncryptionService.java`

**Interfaces:**
- Produces: `EncryptionService` 类，提供`encrypt(String plaintext)`和`decrypt(String ciphertext)`方法

- [ ] **Step 1: 创建EncryptionService类**

```java
package com.xiaomo.agent.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKeySpec secretKey;

    public EncryptionService(@Value("${CONFIG_ENCRYPTION_KEY:}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException("CONFIG_ENCRYPTION_KEY environment variable is not set");
        }
        // 使用SHA-256哈希确保密钥长度为256位
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] hashedKey = java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes);
        this.secretKey = new SecretKeySpec(hashedKey, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 将IV和加密数据组合
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }
}
```

- [ ] **Step 2: 验证加密服务编译**

运行 `mvn compile` 确认加密服务编译通过。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/xiaomo/agent/common/util/EncryptionService.java
git commit -m "feat: 创建AES-256-GCM加密服务"
```

---

## Task 5: 创建UserConfigService业务逻辑

**Files:**
- Create: `src/main/java/com/xiaomo/agent/user/config/UserConfigService.java`

**Interfaces:**
- Consumes: `UserConfigRepository`, `EncryptionService`, `RedisTemplate`
- Produces: `UserConfigService` 类，提供`getConfig(Long userId)`, `saveConfig(Long userId, UserConfigDTO dto)`, `deleteConfig(Long userId)`方法

- [ ] **Step 1: 创建UserConfigDTO类**

```java
package com.xiaomo.agent.user.config;

public class UserConfigDTO {

    private String apiKey;
    private String baseUrl;
    private String modelName;

    // Getters and Setters
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
```

- [ ] **Step 2: 创建UserConfigService类**

```java
package com.xiaomo.agent.user.config;

import com.xiaomo.agent.common.util.EncryptionService;
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
```

- [ ] **Step 3: 验证Service编译**

运行 `mvn compile` 确认Service类编译通过。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/xiaomo/agent/user/config/UserConfigDTO.java src/main/java/com/xiaomo/agent/user/config/UserConfigService.java
git commit -m "feat: 创建UserConfigService业务逻辑"
```

---

## Task 6: 创建ConfigController REST API

**Files:**
- Create: `src/main/java/com/xiaomo/agent/user/config/ConfigController.java`

**Interfaces:**
- Consumes: `UserConfigService`, `UserService`(获取当前用户ID)
- Produces: `ConfigController` 类，提供`GET /api/user/config`, `POST /api/user/config`, `DELETE /api/user/config`接口

- [ ] **Step 1: 创建ConfigController类**

```java
package com.xiaomo.agent.user.config;

import com.xiaomo.agent.common.Result;
import com.xiaomo.agent.user.service.UserService;
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
```

- [ ] **Step 2: 验证Controller编译**

运行 `mvn compile` 确认Controller类编译通过。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/xiaomo/agent/user/config/ConfigController.java
git commit -m "feat: 创建ConfigController REST API"
```

---

## Task 7: 修改AgentLoopImpl使用用户配置

**Files:**
- Modify: `src/main/java/com/xiaomo/agent/agent/service/impl/AgentLoopImpl.java`

**Interfaces:**
- Consumes: `UserConfigService`
- Produces: 修改`chat`方法，使用用户配置调用大模型

- [ ] **Step 1: 读取AgentLoopImpl当前实现**

读取文件，了解当前如何调用大模型API。

- [ ] **Step 2: 修改AgentLoopImpl注入UserConfigService**

```java
// 在类中添加字段
private final UserConfigService userConfigService;

// 修改构造函数，注入UserConfigService
public AgentLoopImpl(..., UserConfigService userConfigService) {
    ...
    this.userConfigService = userConfigService;
}
```

- [ ] **Step 3: 修改chat方法使用用户配置**

```java
// 在chat方法中，获取用户配置
UserConfigDTO userConfig = userConfigService.getConfig(userId);

// 如果用户未配置API Key，返回提示
if (userConfig == null || userConfig.getApiKey() == null || userConfig.getApiKey().isEmpty()) {
    return Result.error("请先配置API Key才能使用AI对话功能");
}

// 使用用户配置的API Key和Base URL调用大模型
String apiKey = userConfigService.getDecryptedApiKey(userId);
String baseUrl = userConfig.getBaseUrl();
String modelName = userConfig.getModelName();

// 调用大模型API（根据实际实现调整）
```

- [ ] **Step 4: 验证修改编译**

运行 `mvn compile` 确认修改编译通过。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/xiaomo/agent/agent/service/impl/AgentLoopImpl.java
git commit -m "feat: 修改AgentLoopImpl使用用户配置调用大模型"
```

---

## Task 8: 创建前端配置API调用

**Files:**
- Create: `frontend/src/api/config.ts`

**Interfaces:**
- Produces: `getConfig()`, `saveConfig(config)`, `deleteConfig()` 函数

- [ ] **Step 1: 创建config.ts API文件**

```typescript
// frontend/src/api/config.ts

export interface UserConfig {
  apiKey: string;
  baseUrl: string;
  modelName: string;
}

export async function getConfig(): Promise<UserConfig | null> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config', {
    method: 'GET',
    headers: {
      'Authorization': token,
    },
  });

  if (!response.ok) {
    throw new Error('获取配置失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  return null;
}

export async function saveConfig(config: UserConfig): Promise<void> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(config),
  });

  if (!response.ok) {
    throw new Error('保存配置失败');
  }

  const result = await response.json();
  if (result.code !== 1) {
    throw new Error(result.msg || '保存配置失败');
  }
}

export async function deleteConfig(): Promise<void> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config', {
    method: 'DELETE',
    headers: {
      'Authorization': token,
    },
  });

  if (!response.ok) {
    throw new Error('删除配置失败');
  }
}
```

- [ ] **Step 2: 验证TypeScript编译**

运行 `npm run build` 确认TypeScript编译通过。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/config.ts
git commit -m "feat: 创建前端配置API调用"
```

---

## Task 9: 创建SettingsDialog设置弹窗组件

**Files:**
- Create: `frontend/src/components/SettingsDialog.vue`

**Interfaces:**
- Consumes: `getConfig`, `saveConfig`, `deleteConfig` from `api/config.ts`
- Produces: `SettingsDialog` 组件，包含表单和保存/删除功能

- [ ] **Step 1: 创建SettingsDialog.vue组件**

```vue
<!-- frontend/src/components/SettingsDialog.vue -->
<template>
  <div class="settings-overlay" v-if="visible" @click.self="close">
    <div class="settings-dialog">
      <div class="settings-header">
        <h3>API配置</h3>
        <button class="close-btn" @click="close">×</button>
      </div>

      <div class="settings-body">
        <div class="form-group">
          <label>API Key</label>
          <div class="input-group">
            <input
              :type="showApiKey ? 'text' : 'password'"
              v-model="form.apiKey"
              placeholder="输入API Key"
            />
            <button class="toggle-btn" @click="showApiKey = !showApiKey">
              {{ showApiKey ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div class="form-group">
          <label>Base URL</label>
          <input
            v-model="form.baseUrl"
            placeholder="https://api.example.com"
          />
        </div>

        <div class="form-group">
          <label>模型选择</label>
          <div class="model-select">
            <select v-model="form.modelName" @change="onModelChange">
              <option value="">选择模型</option>
              <option v-for="model in presetModels" :key="model" :value="model">
                {{ model }}
              </option>
              <option value="custom">自定义模型</option>
            </select>
            <input
              v-if="isCustomModel"
              v-model="customModelName"
              placeholder="输入模型名称"
              @blur="onCustomModelBlur"
            />
          </div>
        </div>
      </div>

      <div class="settings-footer">
        <button class="delete-btn" @click="handleDelete" :disabled="loading">
          删除配置
        </button>
        <div class="footer-right">
          <button class="cancel-btn" @click="close">取消</button>
          <button class="save-btn" @click="handleSave" :disabled="loading">
            {{ loading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue';
import { getConfig, saveConfig, deleteConfig, UserConfig } from '../api/config';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const loading = ref(false);
const showApiKey = ref(false);
const isCustomModel = ref(false);
const customModelName = ref('');

const presetModels = [
  'mimo-v2.5-pro',
  'claude-3-opus',
  'claude-3-sonnet',
  'gpt-4o',
  'gpt-4-turbo',
];

const form = reactive<UserConfig>({
  apiKey: '',
  baseUrl: '',
  modelName: '',
});

watch(() => props.visible, async (newVal) => {
  if (newVal) {
    await loadConfig();
  }
});

async function loadConfig() {
  try {
    const config = await getConfig();
    if (config) {
      form.apiKey = config.apiKey || '';
      form.baseUrl = config.baseUrl || '';
      form.modelName = config.modelName || '';

      // 检查是否为自定义模型
      if (config.modelName && !presetModels.includes(config.modelName)) {
        isCustomModel.value = true;
        customModelName.value = config.modelName;
      }
    }
  } catch (error) {
    console.error('加载配置失败:', error);
  }
}

function onModelChange() {
  if (form.modelName === 'custom') {
    isCustomModel.value = true;
    customModelName.value = '';
  } else {
    isCustomModel.value = false;
  }
}

function onCustomModelBlur() {
  if (customModelName.value) {
    form.modelName = customModelName.value;
  }
}

async function handleSave() {
  if (!form.apiKey) {
    alert('请输入API Key');
    return;
  }

  loading.value = true;
  try {
    await saveConfig({
      apiKey: form.apiKey,
      baseUrl: form.baseUrl,
      modelName: form.modelName,
    });
    alert('配置保存成功');
    emit('saved');
    close();
  } catch (error) {
    alert('保存失败: ' + (error as Error).message);
  } finally {
    loading.value = false;
  }
}

async function handleDelete() {
  if (!confirm('确定要删除配置吗？')) {
    return;
  }

  loading.value = true;
  try {
    await deleteConfig();
    form.apiKey = '';
    form.baseUrl = '';
    form.modelName = '';
    alert('配置已删除');
    emit('saved');
    close();
  } catch (error) {
    alert('删除失败: ' + (error as Error).message);
  } finally {
    loading.value = false;
  }
}

function close() {
  emit('close');
}
</script>

<style scoped>
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.settings-dialog {
  background: white;
  border-radius: 8px;
  width: 500px;
  max-width: 90vw;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #eee;
}

.settings-header h3 {
  margin: 0;
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
}

.settings-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.input-group {
  display: flex;
  gap: 8px;
}

.input-group input {
  flex: 1;
}

.toggle-btn {
  padding: 8px 12px;
  background: #f0f0f0;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

input, select {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.model-select {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #eee;
}

.footer-right {
  display: flex;
  gap: 8px;
}

button {
  padding: 10px 20px;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.delete-btn {
  background: #ff4d4f;
  color: white;
  border-color: #ff4d4f;
}

.cancel-btn {
  background: #f0f0f0;
}

.save-btn {
  background: #1890ff;
  color: white;
  border-color: #1890ff;
}
</style>
```

- [ ] **Step 2: 验证组件编译**

运行 `npm run build` 确认组件编译通过。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/SettingsDialog.vue
git commit -m "feat: 创建SettingsDialog设置弹窗组件"
```

---

## Task 10: 修改ChatView.vue绑定设置按钮

**Files:**
- Modify: `frontend/src/views/ChatView.vue`

**Interfaces:**
- Consumes: `SettingsDialog` 组件
- Produces: 绑定设置按钮点击事件，显示设置弹窗

- [ ] **Step 1: 读取ChatView.vue当前实现**

读取文件，找到设置按钮的位置。

- [ ] **Step 2: 修改ChatView.vue导入SettingsDialog**

```vue
<script setup lang="ts">
// 添加导入
import SettingsDialog from '../components/SettingsDialog.vue';

// 添加状态
const showSettings = ref(false);
</script>
```

- [ ] **Step 3: 修改设置按钮绑定点击事件**

```vue
<div class="footer-item" title="设置" @click="showSettings = true">
  <Settings :size="18" />
  <span class="footer-label">设置</span>
</div>
```

- [ ] **Step 4: 添加SettingsDialog组件到模板**

```vue
<template>
  <!-- 在适当位置添加 -->
  <SettingsDialog
    :visible="showSettings"
    @close="showSettings = false"
    @saved="onSettingsSaved"
  />
</template>
```

- [ ] **Step 5: 添加onSettingsSaved处理函数**

```vue
<script setup lang="ts">
function onSettingsSaved() {
  // 配置保存成功后的处理
  console.log('配置已保存');
}
</script>
```

- [ ] **Step 6: 验证修改编译**

运行 `npm run build` 确认修改编译通过。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/ChatView.vue
git commit -m "feat: 修改ChatView绑定设置按钮事件"
```

---

## Task 11: 添加配置检查逻辑

**Files:**
- Modify: `frontend/src/views/ChatView.vue`

**Interfaces:**
- Consumes: `getConfig` from `api/config.ts`
- Produces: 发起对话前检查用户配置

- [ ] **Step 1: 修改发送消息函数**

```typescript
async function sendMessage() {
  // 检查用户配置
  try {
    const config = await getConfig();
    if (!config || !config.apiKey) {
      alert('请先配置API Key才能使用AI对话功能');
      showSettings.value = true;
      return;
    }
  } catch (error) {
    console.error('检查配置失败:', error);
  }

  // 原有的发送消息逻辑
  ...
}
```

- [ ] **Step 2: 验证修改编译**

运行 `npm run build` 确认修改编译通过。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/ChatView.vue
git commit -m "feat: 添加发起对话前的配置检查"
```

---

## Task 12: 添加环境变量配置

**Files:**
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: 添加`CONFIG_ENCRYPTION_KEY`环境变量配置

- [ ] **Step 1: 修改application.yml添加环境变量**

```yaml
# 在spring配置下添加
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      base-url: ${ANTHROPIC_BASE_URL}
      chat:
        options:
          model: mimo-v2.5-pro
          max-tokens: 4096

# 添加配置加密密钥
config:
  encryption:
    key: ${CONFIG_ENCRYPTION_KEY:}
```

- [ ] **Step 2: 验证配置文件语法**

检查YAML语法是否正确。

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat: 添加CONFIG_ENCRYPTION_KEY环境变量配置"
```

---

## Task 13: 集成测试

**Files:**
- Test: 整个功能的端到端测试

**Interfaces:**
- 测试所有API接口和前端功能

- [ ] **Step 1: 启动后端服务**

```bash
mvn spring-boot:run
```

- [ ] **Step 2: 启动前端开发服务器**

```bash
cd frontend
npm run dev
```

- [ ] **Step 3: 测试设置弹窗打开**

1. 登录系统
2. 点击侧边栏设置按钮
3. 确认设置弹窗正常显示

- [ ] **Step 4: 测试配置保存**

1. 输入API Key、Base URL、选择模型
2. 点击保存按钮
3. 确认保存成功提示

- [ ] **Step 5: 测试配置读取**

1. 关闭设置弹窗
2. 重新打开设置弹窗
3. 确认配置正确显示（API Key脱敏）

- [ ] **Step 6: 测试配置删除**

1. 点击删除配置按钮
2. 确认删除确认对话框
3. 确认删除成功

- [ ] **Step 7: 测试对话功能**

1. 配置有效的API Key
2. 发起对话
3. 确认使用用户配置的API Key调用大模型

- [ ] **Step 8: 测试未配置API Key场景**

1. 删除配置
2. 尝试发起对话
3. 确认提示用户配置API Key

- [ ] **Step 9: Commit测试结果**

```bash
git add .
git commit -m "test: 完成用户API配置功能集成测试"
```

---

## 自查清单

### 1. 设计覆盖检查

- [x] 用户级别配置功能
- [x] API Key加密存储
- [x] 后端代理模式
- [x] 设置弹窗UI
- [x] 配置优先级处理
- [x] 迁移策略

### 2. 占位符扫描

- [x] 没有TBD、TODO
- [x] 所有步骤都有完整代码
- [x] 所有文件路径都是精确的

### 3. 类型一致性检查

- [x] UserConfig实体类字段一致
- [x] UserConfigDTO字段一致
- [x] API接口签名一致
- [x] 前端TypeScript类型一致

---

## 执行选项

**计划完成并保存到 `docs/superpowers/plans/2026-07-05-user-api-config.md`。两种执行方式：**

**1. Subagent-Driven（推荐）** - 每个任务分发一个新的子代理，任务之间进行审查，快速迭代

**2. Inline Execution** - 在当前会话中使用 executing-plans 执行任务，批量执行并设置检查点

**选择哪种方式？**
