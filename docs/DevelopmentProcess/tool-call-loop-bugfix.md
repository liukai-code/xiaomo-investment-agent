# AI 工具调用无限循环 Bug 修复

## 问题描述

用户让 Agent 查询数据库（如"列出前十条消息记录"），后端日志显示同一 SQL 语句被反复执行 8+ 次，每次间隔约 2 秒，最终 120 秒超时报错：

```
流式请求异常: Did not observe any item or terminal signal within 120000ms in 'peek'
```

前端收到的是一个模糊的超时提示，用户无法理解发生了什么。

## 原因分析

### 直接原因：mimo-v2.5-pro 模型陷入工具调用死循环

模型调用 `executeQuery` 拿到查询结果后，没有生成最终回复，而是再次发起相同的 `tool_use` 请求。日志证据：

```
[SqlTool] executeQuery 入参: sql=SELECT id, content, created_at, role, conversation_id FROM chat_messages ORDER BY id ASC LIMIT 10
[SqlTool] executeQuery 出参长度: 1577
// 以上日志重复出现 8 次，SQL 完全相同
```

### 根本原因：三个缺陷叠加

#### 1. Spring AI 1.0.0 没有工具调用迭代上限

`AnthropicChatModel.internalCall()` 的内部逻辑是递归调用：

```
模型返回 tool_use → 执行工具 → 结果拼入对话 → 再次调用模型 → 模型又返回 tool_use → ...
```

这个递归没有任何 guard：没有迭代计数器、没有最大轮次配置。通过反编译 `spring-ai-model-1.0.0.jar` 确认，`ToolCallingManager`、`AnthropicChatOptions`、`DefaultToolCallingManager` 均无相关配置项。

#### 2. 应用层没有任何防护机制

- `AgentLoopImpl.chatStream()` 完全依赖 Spring AI 内部的工具循环，无法感知循环状态
- 没有重复调用检测（同名工具 + 相同参数）
- 唯一的兜底是 `.timeout(Duration.ofSeconds(120))`，但触发后返回的是通用超时错误

#### 3. 工具调用历史未持久化

`buildContext()` 方法（AgentLoopImpl.java:245）只重建 `USER` 和 `ASSISTANT` 两种角色的消息：

```java
for (ChatMessage msg : recentMessages) {
    switch (msg.getRole()) {
        case USER -> context.add(new UserMessage(msg.getContent()));
        case ASSISTANT -> context.add(new AssistantMessage(msg.getContent()));
    }
}
```

中间的工具调用记录（`tool_use` + `tool_result`）存在于 Spring AI 的内存中，请求结束后丢失。虽然 `MessageRole.TOOL` 枚举和 `ChatMessage.toolName`/`toolCallId` 字段已经定义，但从未被使用。

## 修复方案

### 思路：自定义 ToolCallingManager 包装器

Spring AI 自动配置类 `ToolCallingAutoConfiguration` 的 `toolCallingManager()` 方法标注了 `@ConditionalOnMissingBean`，因此提供自定义的 `ToolCallingManager` Bean 可以替换默认实现。`AnthropicChatModel` 会透明使用自定义实现，无需修改工具执行循环本身。

### 修改清单

#### 1. 新建 `MaxToolCallManager.java`

路径：`src/main/java/com/itlk/myclaudecode/agent/service/impl/MaxToolCallManager.java`

实现 `ToolCallingManager` 接口，包装 `DefaultToolCallingManager`，加入两个防护：

- **迭代计数**：`ThreadLocal<Integer>` 跟踪当前轮次，超过 `agent.tool.max-iterations`（默认 10）抛出 `ToolCallLimitExceededException`
- **重复检测**：`ThreadLocal<LinkedHashMap>` LRU 缓存（最大 50 条），key 为 `toolName:argumentsHashCode`，命中缓存直接返回上次结果

#### 2. 新建 `ToolCallLimitExceededException.java`

路径：`src/main/java/com/itlk/myclaudecode/common/exception/ToolCallLimitExceededException.java`

继承 `RuntimeException`，携带友好中文提示："工具调用次数已达上限（N 轮），请尝试简化问题或分步提问"

#### 3. 修改 `AgentLoopImpl.java`

路径：`src/main/java/com/itlk/myclaudecode/agent/service/impl/AgentLoopImpl.java`

- 注入 `MaxToolCallManager`
- `chat()` 和 `chatStream()` 开头调用 `maxToolCallManager.reset()` 重置状态
- `chat()` 加 try-catch 捕获 `ToolCallLimitExceededException`
- `chatStream()` 的 `onErrorResume` 中识别该异常，返回专门的错误消息而非通用超时提示
- 超时时间从 120s 提升到 300s（迭代上限已防止无限循环，超时仅作为兜底）

#### 4. 修改 `application.yml`

添加配置项：

```yaml
agent:
  tool:
    max-iterations: 10
```

## 验证方式

1. 启动后端 `mvn spring-boot:run`
2. 在前端对话中请求 "列出数据库前10条消息记录"
3. 观察日志：`[MaxToolCallManager] 工具调用轮次` 不应超过 10 轮
4. 如果模型仍循环调用，用户应收到 "工具调用次数已达上限" 的友好提示，而非超时错误
