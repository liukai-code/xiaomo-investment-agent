# Agent API 文档

> 最后更新: 2026-07-26 | 共 54 个端点

Base URL: `http://localhost:4545`

## 通用响应格式

所有接口返回统一的 `Result<T>` 结构：

```json
{
  "code": 1,
  "msg": null,
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 1=成功，0=失败 |
| msg | String | 错误信息（成功时为 null） |
| data | T | 业务数据 |

**认证方式**: 除注册/登录/管理后台外，所有接口需在 Header 中携带 `Authorization: Bearer {token}`。

---

## 一、认证 (`/api/auth`)

### 1. 注册

```
POST /api/auth/register
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | 邮箱 |
| password | String | 是 | 密码（≥6位） |

**响应**: `{ id, email, accountId }` — 注册时自动生成唯一六位数字账号 `user_123456` 格式入库，初始免费额度 100,000 tokens。

---

### 2. 登录

```
POST /api/auth/login
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | 邮箱 |
| password | String | 是 | 密码（客户端 SHA-256 → 服务端 BCrypt） |

**响应**: `{ token, userId, email, accountId }` — Token 有效期 72 小时，单设备登录。

---

### 3. 登出

```
POST /api/auth/logout
```

需认证。清除当前 Token。

---

### 4. 获取当前用户

```
GET /api/auth/me
```

需认证。**响应**: `{ id, email, accountId, freeTokenQuota, freeTokenUsed, createdAt }`

---

### 5. 修改密码

```
POST /api/auth/changePassword
```

需认证。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | String | 是 | 旧密码 |
| newPassword | String | 是 | 新密码（≥6位） |

---

### 6. 获取用户偏好

```
GET /api/auth/preferences
```

需认证。**响应**: `{ temperature, maxTokens, contextWindow, memoryEnabled, compressionEnabled }`

---

### 7. 更新用户偏好

```
PUT /api/auth/preferences
```

需认证。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| temperature | Double | 否 | 0~1 |
| maxTokens | Integer | 否 | 100~16384 |
| contextWindow | Integer | 否 | 5~100 |
| memoryEnabled | Boolean | 否 | 记忆总开关 |
| compressionEnabled | Boolean | 否 | 对话摘要压缩开关 |

---

## 二、会话管理 (`/agent/conversation`)

### 8. 创建会话

```
POST /agent/conversation
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 否 | 会话标题，默认 "新对话" |

**响应**: `Conversation` 对象（含 id, title, createdAt, updatedAt）

---

### 9. 会话列表

```
GET /agent/conversation/list
```

按 `updatedAt` 倒序排列。

---

### 10. 获取历史消息

```
GET /agent/conversation/{id}/messages
```

**响应**: `ChatMessage[]` — 每条消息含 id, role, content, toolName, toolCallId, createdAt。

---

### 11. 删除会话

```
DELETE /agent/conversation/{id}
```

---

### 12. 生成会话标题

```
POST /agent/conversation/{id}/generate-title
```

LLM 根据会话前几条消息自动生成简短标题（≤15字）。仅当标题为默认值"新对话"时才会生成新标题。

---

### 13. 保存消息

```
POST /agent/conversation/{id}/message
```

Body 为纯文本内容。用于前端手动保存助手消息。

---

## 三、聊天 (`/agent/chat`)

### 14. 同步聊天

```
GET /agent/chat
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conversationId | Long | 是 | 会话 ID |
| message | String | 是 | 用户消息 |

等待 LLM 完整响应后一次性返回。

---

### 15. 流式聊天

```
GET /agent/chat/stream
```

参数同同步聊天。**响应格式**: Server-Sent Events (SSE)，逐步返回 LLM 生成的内容。

---

### 16. 深度分析（会话内）

```
GET /agent/chat/deep-analysis
```

参数同同步聊天。**响应格式**: SSE，事件类型 `workflow`，包含多智能体工作流的全流程事件。完成后自动保存结果到会话。

---

## 四、深度分析管理 (`/api/analysis`)

### 17. 发起分析

```
POST /api/analysis/start
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | 是 | 分析主题（如 "全面分析贵州茅台"） |

**响应**: `{ analysisId, stockCode, stockName }` — 异步启动工作流，不阻塞。

---

### 18. 分析事件流

```
GET /api/analysis/{id}/stream
```

SSE 实时事件流。支持回放已完成的分析（从数据库重建事件）。

---

### 19. 分析列表

```
GET /api/analysis/list
```

返回当前用户的所有分析记录。

---

### 20. 分析详情

```
GET /api/analysis/{id}
```

---

### 21. 删除分析

```
DELETE /api/analysis/{id}
```

---

### 22. 取消分析

```
POST /api/analysis/{id}/cancel
```

取消正在运行的分析工作流。

---

## 五、用户记忆 (`/memory`)

### 23. 获取用户画像列表

```
GET /memory/profiles
```

**响应**: `ProfileDTO[]` — 含 id, category, content, importance, sourceType, conversationId, active, createdAt, updatedAt。

---

### 24. 添加记忆

```
POST /memory/profiles
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | 是 | 记忆内容 |
| category | String | 是 | 类别（投资风格/风险偏好/关注行业等） |
| conversationId | Long | 否 | 关联会话 |

---

### 25. 更新记忆

```
PUT /memory/profiles/{id}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | 否 | 新内容 |
| importance | Integer | 否 | 重要度 |

---

### 26. 删除记忆

```
DELETE /memory/profiles/{id}
```

---

### 27. 获取会话摘要

```
GET /memory/summaries/{conversationId}
```

返回指定会话的 AI 摘要（消息超 20 条时自动生成，10:1 压缩比）。

---

## 六、通知 (`/api/notifications`)

### 28. 通知列表

```
GET /api/notifications
```

返回最近 50 条通知。

---

### 29. 已读通知 ID

```
GET /api/notifications/read-ids
```

返回已读通知的 ID 集合。

---

### 30. 未读数量

```
GET /api/notifications/unread-count
```

**响应**: `{ count }`

---

### 31. 标记已读

```
POST /api/notifications/{id}/read
```

---

### 32. 隐藏通知

```
POST /api/notifications/{id}/hide
```

---

### 33. 通知实时推送

```
GET /api/notifications/stream
```

SSE 实时推送新通知。

---

## 七、用量统计 (`/api/usage`)

### 34. 用量统计

```
GET /api/usage/stats
```

返回当前用户的 Token 使用统计。

---

### 35. 每日用量

```
GET /api/usage/daily
```

返回每日 Token 使用量列表。

---

### 36. 重置用量

```
DELETE /api/usage/stats
```

重置当前用户的用量统计。

---

## 八、用户配置 (`/api/user/config`)

### 37. 获取配置

```
GET /api/user/config
```

返回用户自定义 API Key 和模型配置。

---

### 38. 保存配置

```
POST /api/user/config
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| apiKey | String | 否 | 自定义 API Key（≥10字符） |
| baseUrl | String | 否 | 自定义 API 地址 |
| model | String | 否 | 自定义模型名 |

---

### 39. 删除配置

```
DELETE /api/user/config
```

删除后回退到全局默认配置。

---

### 40. 测试连接

```
POST /api/user/config/test
```

测试 API 配置是否可用。

---

### 41. 渠道列表

```
GET /api/user/config/channels
```

返回用户的 API 渠道列表。

---

### 42. 获取渠道

```
GET /api/user/config/channels/{channelId}
```

---

### 43. 创建渠道

```
POST /api/user/config/channels
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| channelName | String | 是 | 渠道名称 |
| apiKey | String | 是 | API Key（≥10字符） |
| baseUrl | String | 否 | API 地址 |
| model | String | 否 | 模型名 |

---

### 44. 更新渠道

```
PUT /api/user/config/channels/{channelId}
```

---

### 45. 删除渠道

```
DELETE /api/user/config/channels/{channelId}
```

---

### 46. 激活渠道

```
PUT /api/user/config/channels/{channelId}/activate
```

将指定渠道设为当前活跃渠道。

---

## 九、管理后台

### 47. 管理员登录

```
POST /api/admin/login
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| password | String | 是 | 管理员密码（配置项 `admin.password`） |

**响应**: `{ token }` — 有效期 2 小时。

---

### 48. 创建通知

```
POST /api/admin/notifications
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 通知标题 |
| content | String | 是 | 通知内容 |
| targetUserIds | Long[] | 否 | 目标用户 ID 列表，为空则全体推送 |

---

### 49. 通知列表

```
GET /api/admin/notifications
```

返回所有通知。

---

### 50. 通知接收人

```
GET /api/admin/notifications/{id}/recipients
```

返回指定通知的目标用户 ID 列表。

---

### 51. 删除通知

```
DELETE /api/admin/notifications/{id}
```

---

### 52. 用户列表

```
GET /api/admin/users
```

返回所有用户（id, email, accountId）。

---

## 十、工具开关 (`/api/auth/tools`)

### 53. 工具列表

```
GET /api/auth/tools
```

返回所有工具的启用/禁用状态。**响应**: `{ "a_stock_quote": true, "financial_calc": true, ... }`

---

### 54. 切换工具开关

```
PUT /api/auth/tools/{name}?enabled=true|false
```

动态启用/禁用指定工具。禁用的工具不会出现在模型的 tool list 中，模型完全感知不到。

---

## 十一、消息角色枚举

`ChatMessage.role` 字段取值：

| 值 | 说明 |
|----|------|
| SYSTEM | 系统提示词 |
| USER | 用户消息 |
| ASSISTANT | AI 回复 |
| TOOL | 工具调用结果 |
