# Tool 系统设计文档

> **项目名称：** 金融投资导学 Agent
> **功能模块：** Tool 系统集成
> **创建日期：** 2026-06-24
> **版本：** v1.0

---

## 一、需求概述

### 1.1 功能目标

为金融投资导学 Agent 集成 Tool 调用能力，使 LLM 能够自主决策调用外部工具，扩展 Agent 的能力边界。

### 1.2 本期实现范围

| 范围 | 说明 |
|------|------|
| ✅ 基础文件操作 Tool | read_file, write_file, list_files |
| ✅ Spring AI Function Calling | 使用标准 Function Calling 机制 |
| ✅ 项目目录限制 | Tool 只能访问项目目录下的文件 |
| ✅ 详细错误信息 | 返回详细的错误信息帮助 LLM 理解问题 |
| ❌ 不实现 RAG Tool | 暂不实现金融知识库检索 Tool |
| ❌ 不实现系统信息 Tool | 暂不实现 get_system_info 等 Tool |

### 1.3 设计原则

- **模块化**：每个 Tool 是独立的组件，易于扩展
- **安全性**：限制文件访问范围，防止越权操作
- **可维护性**：清晰的代码结构，易于理解和修改
- **标准化**：遵循 Spring AI Function Calling 标准

---

## 二、技术方案

### 2.1 实现方式

采用 **Spring AI Function Calling** 机制，通过 `@Bean` 注解注册 Function，LLM 自主决策调用。

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| `FileReadTool` | 读取文件内容 |
| `FileWriteTool` | 写入文件内容 |
| `FileListTool` | 列出目录下的文件 |
| `ToolConfig` | 注册所有 Tool 到 Spring 容器 |
| `ToolRegistry` | 管理所有可用 Tool |

### 2.3 数据流

```
用户请求 → LLM 推理 → 决定调用 Tool → Spring AI 执行 Tool → 返回结果 → LLM 继续推理
```

---

## 三、详细设计

### 3.1 目录结构

```
src/main/java/com/xiaomo/agent/
├── agent/
│   ├── service/
│   │   └── Impl/
│   │       └── AgentLoopImpl.java    # [修改] 集成 Tool 系统
│   └── ...
│
├── tool/                              # [新增] Tool 模块
│   ├── FileReadTool.java             # 文件读取 Tool
│   ├── FileWriteTool.java            # 文件写入 Tool
│   ├── FileListTool.java             # 文件列表 Tool
│   └── ToolRegistry.java             # Tool 注册中心
│
└── config/
    └── ToolConfig.java               # [新增] Tool 配置类
```

### 3.2 Tool 接口设计

#### 3.2.1 FileReadTool

**功能**：读取指定文件的内容

**请求参数**：
```java
public record Request(
    @JsonProperty("file_path") String filePath,
    @JsonProperty(value = "encoding", required = false) String encoding
) {}
```

**响应结果**：
```java
public record Response(
    boolean success,
    String content,
    String error
) {}
```

**使用场景**：
- 用户询问"读取 config.yml 文件"
- LLM 需要查看代码文件内容
- 分析日志文件

#### 3.2.2 FileWriteTool

**功能**：写入内容到指定文件

**请求参数**：
```java
public record Request(
    @JsonProperty("file_path") String filePath,
    @JsonProperty("content") String content,
    @JsonProperty(value = "encoding", required = false) String encoding,
    @JsonProperty(value = "append", required = false) Boolean append
) {}
```

**响应结果**：
```java
public record Response(
    boolean success,
    String message,
    String error
) {}
```

**使用场景**：
- 用户要求"创建一个配置文件"
- LLM 需要生成代码文件
- 保存分析结果

#### 3.2.3 FileListTool

**功能**：列出指定目录下的文件

**请求参数**：
```java
public record Request(
    @JsonProperty("dir_path") String dirPath,
    @JsonProperty(value = "recursive", required = false) Boolean recursive,
    @JsonProperty(value = "pattern", required = false) String pattern
) {}
```

**响应结果**：
```java
public record Response(
    boolean success,
    List<FileInfo> files,
    String error
) {}

public record FileInfo(
    String name,
    String path,
    boolean isDirectory,
    long size,
    String lastModified
) {}
```

**使用场景**：
- 用户询问"项目有哪些文件"
- LLM 需要了解项目结构
- 查找特定类型的文件

### 3.3 ToolRegistry 设计

```java
@Component
public class ToolRegistry {

    private final Map<String, Function<?, ?>> tools = new HashMap<>();

    public void register(String name, Function<?, ?> tool) {
        tools.put(name, tool);
    }

    public Function<?, ?> getTool(String name) {
        return tools.get(name);
    }

    public Map<String, Function<?, ?>> getAllTools() {
        return Collections.unmodifiableMap(tools);
    }

    public Set<String> getToolNames() {
        return tools.keySet();
    }
}
```

### 3.4 ToolConfig 设计

```java
@Configuration
public class ToolConfig {

    @Bean
    public FileReadTool fileReadTool() {
        return new FileReadTool();
    }

    @Bean
    public FileWriteTool fileWriteTool() {
        return new FileWriteTool();
    }

    @Bean
    public FileListTool fileListTool() {
        return new FileListTool();
    }

    @Bean
    public ToolRegistry toolRegistry(
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            FileListTool fileListTool) {
        ToolRegistry registry = new ToolRegistry();
        registry.register("read_file", fileReadTool);
        registry.register("write_file", fileWriteTool);
        registry.register("list_files", fileListTool);
        return registry;
    }
}
```

### 3.5 AgentLoopImpl 集成

**修改点**：
1. 注入 `ToolRegistry`
2. 在构建 `Prompt` 时注册 Tool
3. 处理 Tool 调用结果

**关键代码**：
```java
@Service
public class AgentLoopImpl implements AgentLoop {

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final List<Message> history = new ArrayList<>();
    private final String systemPrompt;

    public AgentLoopImpl(ChatModel chatModel,
                         ToolRegistry toolRegistry,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
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

        // 注册所有 Tool
        toolRegistry.getAllTools().forEach((name, tool) ->
                options.function(name, tool));

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

---

## 四、安全设计

### 4.1 路径限制

**原则**：所有文件操作必须限制在项目目录内

**实现**：
```java
private Path validatePath(String filePath) {
    Path path = Paths.get(filePath).normalize();
    Path projectRoot = Paths.get(System.getProperty("user.dir")).normalize();

    if (!path.startsWith(projectRoot)) {
        throw new SecurityException("Access denied: path is outside project directory");
    }

    return path;
}
```

### 4.2 文件大小限制

**原则**：防止读取过大文件导致内存溢出

**实现**：
```java
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

private void checkFileSize(Path path) throws IOException {
    long size = Files.size(path);
    if (size > MAX_FILE_SIZE) {
        throw new IOException("File too large: " + size + " bytes (max: " + MAX_FILE_SIZE + ")");
    }
}
```

### 4.3 错误处理

**原则**：返回详细错误信息，帮助 LLM 理解问题

**实现**：
```java
try {
    // Tool 逻辑
    return new Response(true, content, null);
} catch (SecurityException e) {
    return new Response(false, null, "Security error: " + e.getMessage());
} catch (FileNotFoundException e) {
    return new Response(false, null, "File not found: " + e.getMessage());
} catch (IOException e) {
    return new Response(false, null, "IO error: " + e.getMessage());
} catch (Exception e) {
    return new Response(false, null, "Unexpected error: " + e.getMessage());
}
```

---

## 五、测试计划

### 5.1 单元测试

| 测试用例 | 预期结果 |
|----------|----------|
| 读取存在的文件 | 返回文件内容 |
| 读取不存在的文件 | 返回详细错误信息 |
| 写入新文件 | 文件创建成功 |
| 写入已存在文件 | 文件覆盖成功 |
| 列出目录文件 | 返回文件列表 |
| 访问项目外路径 | 返回安全错误 |

### 5.2 集成测试

| 测试场景 | 用户输入 | 预期行为 |
|----------|----------|----------|
| 读取文件 | "读取 pom.xml 文件" | LLM 调用 read_file Tool |
| 写入文件 | "创建一个 test.txt 文件" | LLM 调用 write_file Tool |
| 列出文件 | "项目有哪些文件" | LLM 调用 list_files Tool |
| 混合操作 | "读取配置文件并修改" | LLM 多次调用 Tool |

### 5.3 测试命令

```bash
# 启动应用
mvn spring-boot:run

# 测试同步对话
curl "http://localhost:4545/agent/chat?message=读取pom.xml文件"

# 测试流式对话
curl -N "http://localhost:4545/agent/chat/stream?message=列出项目文件"
```

---

## 六、实施步骤

### 6.1 步骤 1：创建 Tool 模块

**任务**：
1. 创建 `tool` 包
2. 实现 `FileReadTool`
3. 实现 `FileWriteTool`
4. 实现 `FileListTool`
5. 实现 `ToolRegistry`

### 6.2 步骤 2：创建配置类

**任务**：
1. 创建 `ToolConfig`
2. 注册所有 Tool Bean
3. 配置 `ToolRegistry`

### 6.3 步骤 3：修改 AgentLoopImpl

**任务**：
1. 注入 `ToolRegistry`
2. 修改 `chatStream` 方法，注册 Tool
3. 测试 Tool 调用功能

### 6.4 步骤 4：验证测试

**任务**：
1. 启动应用
2. 测试各个 Tool 功能
3. 验证错误处理
4. 检查安全性

---

## 七、风险评估

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| LLM 不调用 Tool | 功能不可用 | 优化 System Prompt，增加示例 |
| Tool 调用失败 | 对话中断 | 完善错误处理，返回详细信息 |
| 路径遍历攻击 | 安全漏洞 | 严格路径验证，限制访问范围 |
| 文件过大 | 内存溢出 | 限制文件大小，分块读取 |

---

## 八、后续扩展

完成本次 Tool 系统集成后，可按以下顺序扩展：

1. **RAG Tool**：集成金融知识库检索
2. **系统信息 Tool**：获取系统信息、环境变量
3. **代码执行 Tool**：执行简单的代码片段
4. **网络请求 Tool**：调用外部 API
5. **数据库 Tool**：查询数据库

---

## 九、验收标准

- [x] 应用正常启动，无报错
- [x] Tool 注册成功，LLM 可以调用
- [x] 文件读取功能正常
- [x] 文件写入功能正常
- [x] 文件列表功能正常
- [x] 路径限制生效，无法访问项目外文件
- [x] 错误处理完善，返回详细信息
- [x] 流式输出正常工作

---

*文档生成时间：2026-06-24*
*项目仓库：xiaomo-agent*
