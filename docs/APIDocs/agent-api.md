# Agent API 文档

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

---

## 一、会话管理

### 1. 创建会话

```
POST /agent/conversation
```

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 否 | 会话标题，默认 "新对话" |

**请求示例：**

```bash
curl -X POST "http://localhost:4545/agent/conversation?title=投资入门学习"
```

**响应示例：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "id": 1,
    "title": "投资入门学习",
    "createdAt": "2026-06-24T22:10:00",
    "updatedAt": "2026-06-24T22:10:00",
    "messages": []
  }
}
```

---

### 2. 会话列表

```
GET /agent/conversation/list
```

**请求示例：**

```bash
curl "http://localhost:4545/agent/conversation/list"
```

**响应示例：**

```json
{
  "code": 1,
  "msg": null,
  "data": [
    {
      "id": 2,
      "title": "新对话",
      "createdAt": "2026-06-24T22:15:00",
      "updatedAt": "2026-06-24T22:15:00"
    },
    {
      "id": 1,
      "title": "投资入门学习",
      "createdAt": "2026-06-24T22:10:00",
      "updatedAt": "2026-06-24T22:12:00"
    }
  ]
}
```

按 `updatedAt` 倒序排列。

---

### 3. 获取历史消息

```
GET /agent/conversation/{id}/messages
```

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 会话 ID |

**请求示例：**

```bash
curl "http://localhost:4545/agent/conversation/1/messages"
```

**响应示例：**

```json
{
  "code": 1,
  "msg": null,
  "data": [
    {
      "id": 1,
      "role": "USER",
      "content": "什么是基金",
      "toolName": null,
      "toolCallId": null,
      "createdAt": "2026-06-24T22:10:05"
    },
    {
      "id": 2,
      "role": "ASSISTANT",
      "content": "基金是一种集合投资方式...",
      "toolName": null,
      "toolCallId": null,
      "createdAt": "2026-06-24T22:10:08"
    }
  ]
}
```

---

## 二、聊天

### 4. 同步聊天

```
GET /agent/chat
```

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conversationId | Long | 是 | 会话 ID |
| message | String | 是 | 用户消息 |

**请求示例：**

```bash
curl "http://localhost:4545/agent/chat?conversationId=1&message=什么是基金"
```

**响应示例：**

```json
{
  "code": 1,
  "msg": null,
  "data": "基金是一种集合投资方式，由基金管理公司通过发行基金份额，将众多投资者的资金集中起来，由专业基金经理进行投资运作..."
}
```

等待 LLM 完整响应后一次性返回。

---

### 5. 流式聊天

```
GET /agent/chat/stream
```

**参数：** 同同步聊天

**请求示例：**

```bash
curl "http://localhost:4545/agent/chat/stream?conversationId=1&message=什么是基金"
```

**响应格式：** Server-Sent Events (SSE)

```
data: 基金
data: 是一种
data: 集合投资
data: 方式
data: ...
```

逐步返回 LLM 生成的内容，适用于前端实时打字效果。

---

## 三、消息角色枚举

`ChatMessage.role` 字段取值：

| 值 | 说明 |
|----|------|
| SYSTEM | 系统提示词 |
| USER | 用户消息 |
| ASSISTANT | AI 回复 |
| TOOL | 工具调用结果 |
