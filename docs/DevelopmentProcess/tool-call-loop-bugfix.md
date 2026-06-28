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

## 第一轮修复：引入 MaxToolCallManager

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

---

## 第二轮修复：ThreadLocal + 虚拟线程失效

### 问题现象

第一轮修复上线后，AI 模型仍然可以无限调用工具。日志显示每次工具调用轮次始终为 `1/10`，从未递增：

```
[MaxToolCallManager] 工具调用轮次: 1/10
[FinancialCalcTool] calculate 入参: expression=12908.562 / 100000000000000000000
[MaxToolCallManager] 工具调用轮次: 1/10
[FinancialCalcTool] calculate 入参: expression=12908.562 / 1000000000000000000000
[MaxToolCallManager] 工具调用轮次: 1/10
[FinancialCalcTool] calculate 入参: expression=12908.562 / 10000000000000000000000
// 持续 6+ 轮，除数不断增大，轮次始终 1/10
```

同时 AI 模型每次都用不同参数调用（除数递增），重复检测缓存也无法命中。

### 根因分析

**ThreadLocal + 虚拟线程 = 计数器失效。**

Spring Boot 3.5 默认启用虚拟线程（Tomcat 线程池名称 `undedElastic-*`）。`MaxToolCallManager` 使用 `ThreadLocal<Integer>` 存储工具调用轮次计数器，但：

1. `AnthropicChatModel.internalCall()` 每次执行工具调用时，通过虚拟线程池提交任务
2. 每次工具调用可能在**不同的虚拟线程**上执行
3. 每个虚拟线程有独立的 ThreadLocal 副本，初始值为 0
4. 计数器每次都从 0 → 1，永远达不到上限 10

```
请求开始 → reset() → ThreadLocal=0 (虚拟线程A)
  ↓ 第1次工具调用 → 虚拟线程B → ThreadLocal=0 → increment → 1/10 ✓
  ↓ 第2次工具调用 → 虚拟线程C → ThreadLocal=0 → increment → 1/10 ✓  ← 新线程，计数器重置
  ↓ 第3次工具调用 → 虚拟线程D → ThreadLocal=0 → increment → 1/10 ✓  ← 永远 1/10
  ↓ ...无限循环
```

### 修复方案：toolContext 传递 AtomicInteger

利用 Spring AI 的 `ToolCallingChatOptions.toolContext` 机制：在请求开始时将 `AtomicInteger` 计数器放入 `toolContext`，`MaxToolCallManager` 从 `Prompt` 的 options 中提取计数器。

**关键原理：** 通过反编译 `AnthropicChatModel.internalCall()` 字节码确认，工具调用后的递归循环中，Spring AI 会复用同一个 `ChatOptions` 对象：

```
// AnthropicChatModel.internalCall() 字节码 (offset 132-153)
new Prompt(toolExecutionResult.conversationHistory(), originalPrompt.getOptions())
//                                                     ^^^^^^^^^^^^^^^^^^^^^^^^
//                                                     同一个 options 对象引用
```

因此 `toolContext` 中的 `AtomicInteger` 在整轮请求的所有工具调用轮次中共享。

### 修改清单

#### 1. 修改 `MaxToolCallManager.java`

- 移除 `ThreadLocal<Integer> currentIteration`
- 新增常量 `TOOL_CALL_COUNTER_KEY = "toolCallCounter"`
- `executeToolCalls()` 中通过 `extractCounter(prompt)` 从 `Prompt.getOptions().getToolContext()` 提取 `AtomicInteger`
- `reset()` 方法移除计数器重置逻辑（计数器现在由请求方在 options 中初始化）
- 保留 `ThreadLocal<LinkedHashMap> duplicateCache`（重复检测在同一虚拟线程内仍有效）

#### 2. 修改 `AgentLoopImpl.java`

- `chat()` 和 `chatStream()` 构建 `AnthropicChatOptions` 时，注入计数器：

```java
AnthropicChatOptions options = AnthropicChatOptions.builder()
        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
        .temperature(0.7)
        .toolContext(Map.of(MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0)))
        .build();
```

### 验证方式

1. 启动后端 `mvn spring-boot:run`
2. 在前端发送会让模型反复调用工具的问题（如 "12908.562 / 100000000000000000000 等于多少"）
3. 观察日志：轮次应从 1 递增到 10，然后抛出 `ToolCallLimitExceededException`
4. 用户应收到 "工具调用次数已达上限（10 轮），请尝试简化问题或分步提问"

---

## 第三轮修复：软上限 + 模型自主决策

### 问题现象

前两轮修复（计数器 + returnDirect 硬停）生效后，正常多步工具调用被误杀。例如基金分析需要计算多个指标（夏普比率、最大回撤、年化收益等），10 轮工具调用是合理的。硬停导致已计算的部分结果丢失，用户体验差。

### 核心矛盾

- **防无限循环**：需要工具调用上限
- **不限制能力**：复杂任务确实需要很多轮工具调用

### 修复方案：软上限 + 提示模型自行判断

达到阈值后，不中断工具调用循环，而是在工具返回结果中附带系统提示，让模型自行决定是否继续：

```
工具正常执行 → 结果返回 → 达到阈值?
  ├─ 否：正常返回结果
  └─ 是：结果 + "[系统提示] 你已调用 10 轮工具，请判断是否足够..."
         ↓ 计数器重置为 0
         ↓ returnDirect = false（继续循环）
         ↓ 模型看到提示后自行决定：
            ├─ 信息足够 → 直接回答用户
            └─ 仍需补充 → 继续调用工具（新一轮计数）
```

### 安全兜底

| 机制 | 防护目标 |
|------|---------|
| 软上限提示 | 让模型意识到调用次数，主动收敛 |
| 重复调用缓存 | 同名工具 + 相同参数 → 返回缓存结果 |
| SSE 超时 300s | 最终兜底，防止彻底卡死 |

### 修改清单

#### 修改 `MaxToolCallManager.java`

- 移除 `returnDirect(true)` 硬停逻辑
- 达到 `maxIterations` 时：先正常执行工具，然后返回 `WarnedToolExecutionResult`
- `WarnedToolExecutionResult`：在最后一个 `ToolResponseMessage` 的 `responseData` 末尾追加系统提示，`returnDirect = false`
- 计数器重置为 0，允许模型继续调用

```java
if (iteration >= maxIterations) {
    counter.set(0);  // 重置，允许继续
    return new WarnedToolExecutionResult(result, maxIterations);
}
```

提示内容：`[系统提示] 你已在本轮对话中调用了 10 轮工具。请基于已有结果综合分析，判断是否已获得足够信息来回答用户。如果信息充足，请直接回答；如果仍需补充，请继续调用工具。`
