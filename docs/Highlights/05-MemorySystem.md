# 两层记忆系统 -- 让 Agent 记住你是谁、聊过什么

> 本文档是小墨项目技术亮点系列的第 5 篇，面向初次接触项目的开发者，从问题出发，逐步拆解用户画像记忆和对话摘要两层记忆系统的设计思路与实现细节。

---

## 目录

- [一、核心内容](#一核心内容)
- [二、为什么需要这个设计](#二为什么需要这个设计)
- [三、整体架构](#三整体架构)
- [四、代码走读](#四代码走读)
- [五、配置与调参](#五配置与调参)
- [六、实战案例](#六实战案例)
- [七、与其他模块的关系](#七与其他模块的关系)
- [八、常见问题排查](#八常见问题排查)
- [九、源码索引](#九源码索引)
- [十、延伸阅读](#十延伸阅读)

---

## 一、核心内容

- 理解为什么 AI Agent 需要"记忆"，以及两层记忆各自解决什么问题
- 掌握 UserProfile（用户画像）的 6 个类别和 AI 自动提取机制
- 理解 ConversationSummary（对话摘要）的 10:1 压缩比和触发条件
- 了解记忆如何注入到 System Prompt 中影响 LLM 的回答
- 知道如何通过"记住XX"触发用户主动记忆

---

## 二、为什么需要这个设计

### 2.1 问题场景一：Agent 不认识你

用户是价值投资者，偏好长线持有。每次开新会话，Agent 都会问"您的投资风格是什么？"。用户已经说过 10 次了，但 Agent 每次都"失忆"。

### 2.2 问题场景二：上下文窗口不够

用户在一个会话中聊了 50 轮，讨论了茅台的估值、资金面、新闻面。当用户问"综合以上分析，你觉得怎么样？"时，前面 30 轮的内容已经被截断了，Agent 无法综合所有信息。

### 2.3 不这样做的后果

| 场景 | 无记忆 | 有记忆 |
|------|--------|--------|
| 新会话开场 | "您是新手还是老手？" | 知道用户是价值投资者，直接给专业回答 |
| 50 轮对话后 | 前 30 轮被截断，丢失关键信息 | 摘要保留了所有关键决策和数据 |
| 用户说"记住我偏好价值投资" | 不会记住 | 持久化到用户画像，下次自动参考 |

### 2.4 设计目标

1. **跨会话记忆**：用户画像在所有会话间共享，不随会话结束消失
2. **上下文压缩**：长对话自动摘要，10:1 压缩比保留关键信息
3. **双触发机制**：AI 自动提取（每 5 轮）+ 用户主动触发（"记住XX"）
4. **Token 预算控制**：画像 500 token + 摘要 800 token，不挤占对话空间

---

## 三、整体架构

### 3.1 一句话描述

两层记忆：UserProfile 跨会话记住"用户是谁"（投资风格、风险偏好等 6 个维度），ConversationSummary 在长对话中压缩历史消息保留关键信息。两者共同注入 System Prompt，让 LLM 具备"记忆"。

### 3.2 架构图

```mermaid
flowchart TD
    subgraph Layer1["第一层：用户画像 UserProfile"]
        UP1[投资风格]
        UP2[风险偏好]
        UP3[关注板块]
        UP4[持仓习惯]
        UP5[投资经验]
        UP6[其他偏好]
    end

    subgraph Layer2["第二层：对话摘要 ConversationSummary"]
        CS[压缩后的对话摘要<br/>10:1 压缩比]
    end

    subgraph Trigger["触发机制"]
        AUTO[AI 自动提取<br/>每 5 轮对话触发]
        USER[用户主动触发<br/>"记住XX"]
        COMP[对话压缩<br/>消息 > 20 条触发]
    end

    subgraph Inject["注入 System Prompt"]
        SP[System Prompt]
        SP --> SP1["[用户画像记忆]<br/>- [投资风格] 偏好价值投资<br/>- [风险偏好] 稳健型"]
        SP --> SP2["[对话历史摘要]<br/>用户之前讨论了茅台的估值..."]
    end

    AUTO --> Layer1
    USER --> Layer1
    COMP --> Layer2

    Layer1 --> Inject
    Layer2 --> Inject

    Inject --> LLM[LLM 参考记忆回答]

    style Layer1 fill:#e3f2fd
    style Layer2 fill:#fff3e0
```

### 3.3 核心组件表

| 组件 | 文件路径 | 职责 |
|------|---------|------|
| MemoryService | `memory/service/MemoryService.java` | 记忆服务接口（画像 CRUD + 摘要 + Prompt 构建） |
| MemoryServiceImpl | `memory/service/impl/MemoryServiceImpl.java` | 实现：Redis 缓存 + 主动记忆检测 + Prompt 注入 |
| MemoryExtractionService | `memory/service/MemoryExtractionService.java` | 异步记忆提取接口 |
| MemoryExtractionServiceImpl | `memory/service/impl/MemoryExtractionServiceImpl.java` | AI 自动提取画像 + 对话压缩 |
| UserProfile | `memory/entity/UserProfile.java` | 用户画像实体（6 类别 + 重要性 + 来源） |
| ProfileCategory | `memory/entity/ProfileCategory.java` | 画像类别枚举 |
| ConversationSummary | `memory/entity/ConversationSummary.java` | 对话摘要实体 |
| MemoryController | `memory/controller/MemoryController.java` | REST API（画像 CRUD + 摘要查询） |

---

## 四、代码走读

### 4.1 用户画像：6 个维度

```java
// ProfileCategory.java
public enum ProfileCategory {
    INVESTMENT_STYLE("投资风格"),   // 价值投资、趋势交易、打板、量化
    RISK_PREFERENCE("风险偏好"),    // 保守、稳健、激进
    FOCUS_SECTOR("关注板块"),       // 半导体、新能源、消费
    HOLDING_HABIT("持仓习惯"),      // 短线、中线、长线
    EXPERIENCE_LEVEL("投资经验"),   // 新手、进阶、资深
    GENERAL("其他偏好");            // 其他
}
```

每条画像记忆包含：
- `category`：类别
- `content`：记忆内容（一句话）
- `importance`：重要性 1-5（用户主动说"记住"的默认为 5）
- `sourceType`：来源（`USER_EXPLICIT` 用户主动 / `AI_EXTRACTED` AI 提取）
- `active`：是否启用（软删除）

### 4.2 用户主动记忆："记住XX"

用户在对话中说"记住我偏好价值投资"，系统通过正则匹配检测：

```java
// MemoryServiceImpl.java — 检测主动记忆
private static final Pattern EXPLICIT_MEMORY_PATTERN = Pattern.compile(
        "(?:记住|记一下|帮我记|请记住|请记下|记着|别忘了|帮我记住)[:：]?\\s*(.+)",
        Pattern.DOTALL);

public DetectResult detectExplicitMemory(String message) {
    Matcher matcher = EXPLICIT_MEMORY_PATTERN.matcher(message.trim());
    if (!matcher.find()) return new DetectResult(false, null, null);

    String content = matcher.group(1).trim();
    ProfileCategory category = inferCategory(content);  // 根据内容推断类别
    return new DetectResult(true, content, category);
}
```

`inferCategory()` 根据关键词推断类别：
- 包含"风格/价值投资/趋势/打板" → `INVESTMENT_STYLE`
- 包含"风险/保守/稳健/激进" → `RISK_PREFERENCE`
- 包含"板块/行业/半导体" → `FOCUS_SECTOR`
- 其他 → `GENERAL`

### 4.3 AI 自动提取：每 5 轮触发

`MemoryExtractionServiceImpl` 在每次对话结束后异步检查：

```java
// MemoryExtractionServiceImpl.java — 触发条件
private static final int PROFILE_EXTRACTION_INTERVAL = 5;

private boolean shouldExtractProfile(Long conversationId) {
    long totalMessages = chatMessageRepository.countByConversationId(conversationId);
    return totalMessages > 0 && totalMessages % PROFILE_EXTRACTION_INTERVAL == 0;
}
```

每 5 轮对话触发一次 AI 提取，提取流程：
1. 获取最近 10 条消息
2. 获取已有画像（用于去重）
3. 调用 LLM（temperature=0.1）提取画像，输出 JSON 数组
4. 解析结果，去重后保存到 `user_profiles` 表

### 4.4 对话摘要：10:1 压缩

当未压缩消息超过 20 条时，触发对话压缩：

```java
// MemoryExtractionServiceImpl.java — 压缩触发
private static final int COMPRESSION_THRESHOLD = 20;
private static final int BATCH_SIZE = 15;
private static final int KEEP_RECENT = 5;
```

压缩流程：
1. 取前 15 条未压缩消息（保留最近 5 条在上下文中）
2. 调用 LLM（temperature=0.2）压缩为摘要，压缩比约 10:1
3. 如果已有旧摘要，合并新旧内容去重
4. 保存到 `conversation_summaries` 表

### 4.5 记忆注入：buildMemoryPrompt()

记忆通过 `buildMemoryPrompt()` 方法注入到 System Prompt 中：

```java
// MemoryServiceImpl.java — 记忆注入
public String buildMemoryPrompt(Long userId, Long conversationId) {
    StringBuilder sb = new StringBuilder();

    // 1. 用户画像记忆（token 预算 500）
    List<UserProfile> profiles = getActiveProfiles(userId);
    if (!profiles.isEmpty()) {
        sb.append("\n\n[用户画像记忆]\n");
        sb.append("以下是该用户的历史偏好信息，请在回答时参考，但不要直接提及你记住了这些：\n");
        int tokenBudget = 0;
        for (UserProfile p : profiles) {
            String line = "- [" + p.getCategory().getDisplayName() + "] " + p.getContent();
            if (tokenBudget + estimateTokens(line) > PROFILE_TOKEN_BUDGET) break;
            sb.append(line).append("\n");
            tokenBudget += estimateTokens(line);
        }
    }

    // 2. 对话摘要（token 预算 800）
    ConversationSummary summary = getLatestSummary(conversationId);
    if (summary != null) {
        sb.append("\n\n[对话历史摘要]\n");
        sb.append("以下是本会话早期被压缩的对话摘要，用于保持上下文连续性：\n");
        sb.append(truncateToTokenLimit(summary.getSummary(), SUMMARY_TOKEN_BUDGET));
    }

    return sb.toString();
}
```

**关键设计**：
- 画像和摘要各有独立的 token 预算（500 + 800 = 1300 token）
- 画像按重要性降序排列，超出预算的截断
- 提示 LLM "不要直接提及你记住了这些"，避免让用户觉得被监视

---

## 五、配置与调参

| 配置项 | 位置 | 默认值 | 说明 |
|--------|------|--------|------|
| `PROFILE_EXTRACTION_INTERVAL` | 常量 | `5` | 每隔多少轮触发画像提取 |
| `COMPRESSION_THRESHOLD` | 常量 | `20` | 未压缩消息超过此数量触发压缩 |
| `BATCH_SIZE` | 常量 | `15` | 每批压缩的消息数 |
| `KEEP_RECENT` | 常量 | `5` | 压缩后保留最近 N 条在上下文中 |
| `PROFILE_TOKEN_BUDGET` | 常量 | `500` | 画像注入的 token 预算 |
| `SUMMARY_TOKEN_BUDGET` | 常量 | `800` | 摘要注入的 token 预算 |
| `MAX_PROFILES_PER_USER` | 常量 | `50` | 每用户最大画像记忆数 |
| 用户设置 | 前端设置 | 开启 | 用户可关闭"对话摘要压缩"功能 |

---

## 六、实战案例

### 6.1 用户主动记忆

```
用户: 记住我偏好价值投资，关注消费和医药板块
→ detectExplicitMemory() 匹配成功
→ content="偏好价值投资，关注消费和医药板块"
→ inferCategory() → INVESTMENT_STYLE
→ addUserMemory(userId, content, INVESTMENT_STYLE)
→ 保存到 user_profiles 表，importance=5

下次新会话时，System Prompt 包含：
[用户画像记忆]
- [投资风格] 偏好价值投资，关注消费和医药板块
```

### 6.2 AI 自动提取

```
第 10 轮对话结束后（10 % 5 == 0）
→ extractMemoriesAsync() 触发
→ 获取最近 10 条消息
→ LLM 提取：[{"category":"RISK_PREFERENCE","content":"用户倾向于稳健型投资","importance":3}]
→ 去重检查：已有画像中没有相同内容
→ 保存到 user_profiles 表，sourceType=AI_EXTRACTED
```

### 6.3 对话压缩

```
第 25 轮对话结束后，未压缩消息 = 25 条 > 20 条阈值
→ compressConversation() 触发
→ 取前 15 条消息压缩，保留最近 5 条
→ LLM 输出摘要："用户讨论了茅台的估值（PE 28.5）和资金面（北向资金流入），倾向于中长期持有..."
→ 保存到 conversation_summaries 表，compressedCount=15

后续对话的 System Prompt 包含：
[对话历史摘要]
用户讨论了茅台的估值（PE 28.5）和资金面（北向资金流入），倾向于中长期持有...
```

---

## 七、与其他模块的关系

```mermaid
flowchart LR
    Agent[AgentLoopImpl] -->|调用| MS[MemoryService]
    MS -->|buildMemoryPrompt()| CB[ContextBuilder]
    CB -->|注入到 System Prompt| SCC[Spring AI ChatClient]

    MES[MemoryExtractionService] -->|异步提取| AI[LLM]
    MES -->|保存| DB[(user_profiles<br/>conversation_summaries)]
    MES -->|读取消息| CMR[ChatMessageRepository]

    MC[MemoryController] -->|REST API| MS
    FE[前端设置] -->|开关| Agent

    style MS fill:#e3f2fd
    style MES fill:#fff3e0
```

修改记忆系统时需要注意的联动点：
- 修改 `ProfileCategory` → 同步更新 `inferCategory()` 的关键词映射
- 修改 token 预算 → 影响 System Prompt 的长度和对话上下文空间
- 修改压缩阈值 → 影响长对话的上下文保留质量

---

## 八、常见问题排查

| 现象 | 可能原因 | 排查方法 |
|------|---------|---------|
| 记忆未生效 | 用户在设置中关闭了记忆功能 | 检查用户的 memoryEnabled 配置 |
| 画像提取未触发 | 消息数不是 5 的倍数 | 检查 `shouldExtractProfile()` 逻辑 |
| 对话压缩未触发 | 消息数 < 20 或用户关闭了压缩 | 检查 `compressionEnabled` 配置 |
| 画像重复 | AI 提取的去重检查基于精确匹配 | 检查 `existsByUserIdAndContent()` |
| 记忆占用过多 token | 画像数量过多 | 检查 `MAX_PROFILES_PER_USER` 和 token 预算 |
| "记住"未被检测 | 正则不匹配 | 检查 `EXPLICIT_MEMORY_PATTERN` 是否覆盖用户的表达方式 |

---

## 九、源码索引

| 文件 | 路径 | 关键方法 |
|------|------|---------|
| MemoryService | `memory/service/MemoryService.java` | 接口定义 |
| MemoryServiceImpl | `memory/service/impl/MemoryServiceImpl.java` | `getActiveProfiles()`, `addUserMemory()`, `buildMemoryPrompt()`, `detectExplicitMemory()` |
| MemoryExtractionService | `memory/service/MemoryExtractionService.java` | 接口定义 |
| MemoryExtractionServiceImpl | `memory/service/impl/MemoryExtractionServiceImpl.java` | `extractMemoriesAsync()`, `extractProfileMemories()`, `compressConversation()` |
| UserProfile | `memory/entity/UserProfile.java` | JPA 实体 |
| ProfileCategory | `memory/entity/ProfileCategory.java` | 6 个类别枚举 |
| ConversationSummary | `memory/entity/ConversationSummary.java` | JPA 实体 |
| MemorySourceType | `memory/entity/MemorySourceType.java` | AI_EXTRACTED / USER_EXPLICIT |
| MemoryController | `memory/controller/MemoryController.java` | REST API |
| ContextBuilder | `agent/service/impl/ContextBuilder.java` | 调用 `buildMemoryPrompt()` 注入 System Prompt |

---

## 十、延伸阅读

- [意图分类 + 工具过滤](03-IntentClassificationAndToolFiltering.md) — 记忆系统与意图分类的交互
- [SSE 流式架构](06-SSEStreamingAndNotification.md) — 对话消息的存储和流式推送
- [用户配置系统](07-UserConfigSystem.md) — 记忆开关的配置管理
