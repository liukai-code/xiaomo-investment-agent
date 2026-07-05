# 用户大模型API配置功能设计

## 背景

当前系统使用环境变量配置大模型API密钥，所有用户共享同一配置。为支持多用户场景，需要让每个用户配置自己的API密钥，同时保证密钥安全。

## 设计目标

1. 用户级别配置：每个用户配置自己的API Key、Base URL、模型选择
2. 安全存储：API Key加密存储在数据库，前端不持久化
3. 后端代理：保持现有架构，后端代理调用大模型API
4. 用户体验：设置弹窗、下拉列表选择模型、配置验证

## 架构设计

### 数据流

```
用户 → 前端设置弹窗 → 后端API → 加密存储 → 数据库
                ↓
用户发起对话 → 后端读取配置 → 解密API Key → 调用大模型API
```

### 核心组件

| 组件 | 说明 |
|------|------|
| SettingsDialog.vue | 前端设置弹窗组件 |
| ConfigController | 后端配置管理API |
| UserConfigService | 用户配置业务逻辑 |
| UserConfigRepository | 数据访问层 |
| EncryptionService | API Key加密解密服务 |

## 数据库设计

### 用户配置表（user_config）

```sql
CREATE TABLE user_config (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  api_key_encrypted VARCHAR(512),  -- 加密后的API Key
  base_url VARCHAR(256),           -- API基础URL
  model_name VARCHAR(128),         -- 模型名称
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_user_config_user_id ON user_config(user_id);
```

### Redis缓存结构

- **Key**: `user:config:{userId}`
- **Type**: Hash
- **Fields**: `base_url`, `model_name`, `api_key_encrypted`
- **TTL**: 24小时

## 后端API设计

### 配置管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/user/config | 获取当前用户配置 |
| POST | /api/user/config | 保存用户配置 |
| DELETE | /api/user/config | 删除用户配置 |

### 请求/响应格式

**POST /api/user/config**
```json
{
  "apiKey": "sk-xxx",
  "baseUrl": "https://api.example.com",
  "modelName": "mimo-v2.5-pro"
}
```

**GET /api/user/config**
```json
{
  "code": 1,
  "data": {
    "apiKey": "sk-***xyz",
    "baseUrl": "https://api.example.com",
    "modelName": "mimo-v2.5-pro"
  }
}
```

### 安全措施

- API Key在传输时使用HTTPS加密
- API Key在存储时使用AES-256-GCM加密
- GET接口返回脱敏的API Key（前3位 + *** + 后3位）
- 用户只能访问自己的配置

## 前端设计

### 设置弹窗组件（SettingsDialog.vue）

**布局**：
- API Key：密码输入框，带显示/隐藏切换
- Base URL：文本输入框
- 模型选择：下拉列表 + 手动输入支持

**预设模型列表**：
```
mimo-v2.5-pro
claude-3-opus
claude-3-sonnet
gpt-4o
gpt-4-turbo
```

### 交互流程

1. 用户点击侧边栏设置按钮，弹出设置弹窗
2. 弹窗加载用户现有配置（如果有）
3. 用户修改配置并点击保存
4. 保存成功后显示成功提示，关闭弹窗
5. 如果用户未配置API Key，发起对话时提示用户先配置

### 错误处理

- API Key格式验证（基本格式检查）
- 保存失败时显示错误提示
- 网络错误时显示重试提示

## 加密方案

### API Key加密

- **算法**：AES-256-GCM（对称加密）
- **密钥管理**：从环境变量 `CONFIG_ENCRYPTION_KEY` 读取加密密钥
- **加密流程**：
  1. 前端发送明文API Key（HTTPS传输）
  2. 后端使用AES-256-GCM加密API Key
  3. 加密后的密文存储到数据库
  4. 读取时解密使用

### 环境变量

```bash
CONFIG_ENCRYPTION_KEY=your-256-bit-key  # 用于加密用户配置
```

## 配置优先级

**仅用户配置模式**：
- 用户必须配置API Key才能使用系统
- 没有全局默认配置
- 新用户首次使用时提示配置

### 迁移策略

**现有环境变量配置处理**：
- 环境变量配置仍保留，作为系统级备用配置
- 如果用户未配置API Key，系统使用环境变量配置（向后兼容）
- 未来版本可以移除环境变量配置，强制用户配置

**新用户引导**：
- 新用户首次发起对话时，检测到未配置API Key
- 弹出提示："请先配置API Key才能使用AI对话功能"
- 提供"立即配置"按钮，打开设置弹窗

## 实现计划

### 后端任务

1. 创建UserConfig实体和Repository
2. 实现EncryptionService（AES-256-GCM加密解密）
3. 实现UserConfigService（业务逻辑）
4. 实现ConfigController（REST API）
5. 修改AgentLoopImpl，使用用户配置调用大模型
6. 添加Redis缓存支持

### 前端任务

1. 创建SettingsDialog.vue组件
2. 实现配置API调用（api/config.ts）
3. 修改ChatView.vue，绑定设置按钮事件
4. 添加配置验证和错误处理
5. 修改对话发起逻辑，检查用户配置

## 验证计划

### 功能验证

1. 用户可以打开设置弹窗
2. 用户可以保存API Key、Base URL、模型选择
3. 保存后配置正确显示（API Key脱敏）
4. 用户可以修改配置
5. 用户可以删除配置
6. 发起对话时使用用户配置的API Key

### 安全验证

1. API Key在数据库中加密存储
2. GET接口返回脱敏数据
3. 用户无法访问其他用户的配置
4. 加密解密功能正常工作

### 错误处理验证

1. 未配置API Key时提示用户
2. API Key格式错误时显示错误
3. 网络错误时显示重试提示
4. 保存失败时显示错误信息
