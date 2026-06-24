# Agent 架构重构设计文档

> **项目名称：** 金融投资导学 Agent
> **改造阶段：** Phase 3 - Agent 架构重构（最小改动方案）
> **创建日期：** 2026-06-24
> **版本：** v1.0

---

## 一、改造目标

将当前的单轮对话系统重构为支持 Agent 架构的对话系统，为未来集成 Tool 系统（如 RAG 知识库检索）做好准备。

### 1.1 改造范围

| 范围 | 说明 |
|------|------|
| ✅ ChatModel 构建方式 | 从手动构建改为 Spring AI 自动配置 |
| ✅ 预留 Tool 接口 | 为未来 Function Calling 做准备 |
| ✅ 保持 SSE 流式输出 | 维持现有的流式对话体验 |
| ✅ 保持单用户设计 | 暂不实现多用户会话隔离 |
| ❌ 不实现具体 Tool | 暂不实现 RAG 检索等 Tool |
| ❌ 不修改前端页面 | 保持现有前端不变 |

### 1.2 改造原则

- **最小改动**：只做必要的架构调整，不引入额外复杂度
- **向后兼容**：保持现有 API 接口不变
- **渐进式扩展**：为未来功能扩展预留空间

---

## 二、当前架构分析

### 2.1 现有代码结构

```
src/main/java/com/itlk/myclaudecode/agent/
├── congfig/
│   └── AnthropicConfig.java      # 配置类（手动读取配置）
├── controller/
│   └── agentLoopController.java  # REST 接口
├── Entity/
│   └── Result.java               # 响应封装
└── service/
    ├── AgentLoop.java            # 服务接口
    └── Impl/
        └── AgentLoopImpl.java    # 核心实现
```

### 2.2 当前实现方式

**ChatModel 构建**（手动方式）：
```java
private AnthropicChatModel getChatModel() {
    if (chatModel == null) {
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(anthropicConfig.getBaseurl())
                .apiKey(anthropicConfig.getApikey())
                .build();
        chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(anthropicConfig.getModel())
                        .maxTokens(4096)
                        .build())
                .build();
    }
    return chatModel;
}
```

**对话流程**：
1. 用户消息 → 添加到历史列表
2. 调用 `chatModel.call()` 或 `chatModel.stream()`
3. 将 AI 回复添加到历史列表
4. 返回给用户

### 2.3 存在的问题

1. **手动构建 ChatModel**：代码冗余，不利于维护
2. **无 Tool 支持**：无法集成 Function Calling
3. **配置分散**：配置读取与业务逻辑耦合

---

## 三、改造方案

### 3.1 技术方案

采用 **Spring AI 自动配置** 方式，通过 `application.yml` 配置 ChatModel，简化代码结构。

#### 3.1.1 配置变更

**application.yml** 新增配置：
```yaml
spring:
  ai:
    anthropic:
      base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: ${ANTHROPIC_MODEL:claude-sonnet-4-20250514}
          max-tokens: 4096
```

#### 3.1.2 代码变更

**AgentLoopImpl.java** 重构：
```java
@Service
public class AgentLoopImpl implements AgentLoop {

    private final ChatModel chatModel;
    private final List<Message> history = new ArrayList<>();
    private final String systemPrompt;

    // 通过构造函数注入 Spring AI 自动配置的 ChatModel
    public AgentLoopImpl(ChatModel chatModel,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String chat(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);
        ChatResponse response = chatModel.call(new Prompt(history));
        AssistantMessage assistantMessage = response.getResult().getOutput();
        history.add(assistantMessage);
        return assistantMessage.getText();
    }

    @Override
    public Flux<String> chatStream(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder accumulated = new StringBuilder();

        return chatModel.stream(new Prompt(history))
                .filter(chatResponse -> chatResponse.getResult() != null
                        && chatResponse.getResult().getOutput() != null)
                .mapNotNull(chatResponse -> chatResponse.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isEmpty())
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    AssistantMessage assistantMessage = new AssistantMessage(accumulated.toString());
                    history.add(assistantMessage);
                });
    }
}
```

### 3.2 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `application.yml` | 修改 | 添加 Spring AI Anthropic 配置 |
| `AgentLoopImpl.java` | 重构 | 使用 Spring AI 自动配置的 ChatModel |
| `AnthropicConfig.java` | 删除 | 不再需要手动配置类 |
| `pom.xml` | 检查 | 确认依赖完整 |

### 3.3 依赖检查

当前 `pom.xml` 已包含必要依赖：
```xml
<!-- Spring AI Anthropic -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

无需额外添加依赖。

---

## 四、实施步骤

### 4.1 步骤 1：修改 application.yml

**文件路径**：`src/main/resources/application.yml`

**变更内容**：
```yaml
spring:
  ai:
    anthropic:
      base-url: https://token-plan-cn.xiaomimimo.com/anthropic
      api-key: tp-c14c7a5f6f4e4c8a9b2d3e5f6a7b8c9d
      chat:
        options:
          model: mimo-v2.5-pro
          max-tokens: 4096

system-default-prompt: |
  你是一位专业的金融投资导学助手，帮助用户学习金融投资知识。
  请用通俗易懂的语言解释金融概念，适合初学者理解。
```

### 4.2 步骤 2：重构 AgentLoopImpl

**文件路径**：`src/main/java/com/itlk/myclaudecode/agent/service/Impl/AgentLoopImpl.java`

**变更内容**：
- 移除 `AnthropicConfig` 依赖
- 移除手动构建 `ChatModel` 的代码
- 通过构造函数注入 `ChatModel`
- 使用 `@Value` 注解读取系统提示词

### 4.3 步骤 3：删除 AnthropicConfig

**文件路径**：`src/main/java/com/itlk/myclaudecode/agent/congfig/AnthropicConfig.java`

**变更内容**：删除整个文件

### 4.4 步骤 4：验证测试

1. 启动应用，确认无启动错误
2. 测试同步对话接口：`GET /agent/chat?message=你好`
3. 测试流式对话接口：`GET /agent/chat/stream?message=你好`
4. 验证对话历史功能正常

---

## 五、风险评估

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| Spring AI 自动配置失败 | 应用无法启动 | 检查配置格式，查看启动日志 |
| ChatModel 注入失败 | 对话功能不可用 | 确认依赖完整，检查配置项 |
| 流式输出异常 | 用户体验下降 | 测试流式接口，检查响应格式 |

---

## 六、后续扩展

完成本次重构后，可按以下顺序扩展功能：

1. **实现 Tool 接口**：创建 `FinancialKnowledgeTool`
2. **集成 Function Calling**：修改 ChatModel 配置，注册 Tool
3. **实现 RAG 检索**：集成向量数据库和知识库
4. **优化会话管理**：实现多用户会话隔离

---

## 七、验收标准

- [x] 应用正常启动，无报错
- [x] 同步对话接口正常工作
- [x] 流式对话接口正常工作
- [x] 对话历史功能正常
- [x] 代码结构更简洁，易于维护

---

## 八、实际实现调整记录

### 8.1 流式输出问题修复

**问题描述**：
初始实现中，流式接口返回 500 错误。

**原因分析**：
Spring AI 自动配置的 ChatModel 在流式模式下需要显式禁用 thinking 模式。

**解决方案**：
在 `chatStream` 方法中添加 `AnthropicChatOptions` 配置：

```java
AnthropicChatOptions options = AnthropicChatOptions.builder()
        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
        .build();

return chatModel.stream(new Prompt(history, options))
        // ... 其余代码
```

### 8.2 最终代码实现

**AgentLoopImpl.java** 最终版本：
```java
@Service
public class AgentLoopImpl implements AgentLoop {

    private final ChatModel chatModel;
    private final List<Message> history = new ArrayList<>();
    private final String systemPrompt;

    public AgentLoopImpl(ChatModel chatModel,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String chat(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);
        ChatResponse response = chatModel.call(new Prompt(history));
        AssistantMessage assistantMessage = response.getResult().getOutput();
        history.add(assistantMessage);
        return assistantMessage.getText();
    }

    @Override
    public Flux<String> chatStream(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder accumulated = new StringBuilder();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .build();

        return chatModel.stream(new Prompt(history, options))
                .filter(chatResponse -> chatResponse.getResult() != null
                        && chatResponse.getResult().getOutput() != null)
                .mapNotNull(chatResponse -> chatResponse.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isEmpty())
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    AssistantMessage assistantMessage = new AssistantMessage(accumulated.toString());
                    history.add(assistantMessage);
                });
    }
}
```

### 8.3 测试结果

**测试环境**：
- 端口：4548
- 测试时间：2026-06-24

**测试用例**：

| 接口 | 请求 | 响应 | 状态 |
|------|------|------|------|
| 同步对话 | `GET /agent/chat?message=你好` | `{"code":1,"msg":null,"data":"你好，我是刘金亮..."}` | ✅ 正常 |
| 流式对话 | `GET /agent/chat/stream?message=你好` | SSE 流式响应 | ✅ 正常 |

---

*文档生成时间：2026-06-24*
*最后更新时间：2026-06-24*
*项目仓库：my-claude-code*
