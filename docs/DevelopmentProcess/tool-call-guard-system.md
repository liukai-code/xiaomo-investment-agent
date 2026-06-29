# 工具调用防护系统（Tool Call Guard System）

## 一、问题背景

AI Agent 在执行工具调用（Tool Calling）时，存在以下循环风险：

| 场景 | 原因 | 后果 |
|------|------|------|
| 搜索-抓取死循环 | 搜索返回链接 → 抓取失败/内容不足 → 再搜索 | Token 消耗爆炸，响应超时 |
| 重复调用同一工具 | 模型对结果不满意，反复调用相同工具"优化" | 浪费 API 调用，无新信息产出 |
| 搜索无限重试 | 搜索被拒后模型换个关键词再搜 | 搜索次数不受控 |
| 工具执行挂起 | 网页抓取 DNS 解析超时、目标服务器无响应 | 整个 Agent 循环阻塞 |
| 非确定性工具缓存失效 | 行情数据每次不同，但缓存 key 相同 | 返回过时数据 |

**核心矛盾：** Agent 需要足够的工具调用自由度来完成复杂任务（如金融分析可能需要 10+ 次调用），但又必须防止失控的无限循环。

---

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                     AgentLoopImpl / AnalystNode                  │
│                    (创建 toolContext，注入所有 Tracker)            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ toolContext Map
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MaxToolCallManager                          │
│              (实现 Spring AI ToolCallingManager 接口)             │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐│
│  │ Counter  │ │ InfoGain │ │Repetition│ │  Fetch   │ │ Search ││
│  │ (Atomic  │ │ Tracker  │ │ Detector │ │ Session  │ │ Session││
│  │ Integer) │ │          │ │          │ │ Tracker  │ │ Tracker││
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └───┬────┘│
│       │            │            │             │           │      │
│       └────────────┴────────────┴─────────────┴───────────┘      │
│                              │                                    │
│                              ▼                                    │
│                    ┌──────────────────┐                           │
│                    │   GuardSignal    │                           │
│                    │ (分级信号评估器)  │                           │
│                    └────────┬─────────┘                           │
│                             │                                     │
│              ┌──────────────┼──────────────┐                      │
│              ▼              ▼              ▼                      │
│         [NONE]        [ADVISORY/       [CRITICAL/                │
│        正常执行        WARNING]         FORCE]                   │
│                        注入信号         强制停止                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件

| 组件 | 职责 | 存储方式 |
|------|------|---------|
| `MaxToolCallManager` | 包装 Spring AI 的 `DefaultToolCallingManager`，在每次工具调用前执行防护检查 | Spring Bean |
| `GuardSignal` | 综合所有信号，输出分级告警（NONE/ADVISORY/WARNING/CRITICAL/FORCE） | Record |
| `InfoGainTracker` | 滑动窗口 + 加权相似度，检测"最近几轮没新信息" | toolContext |
| `RepetitionDetector` | 按工具名追踪参数历史，检测"卡住" | toolContext |
| `FetchSessionTracker` | URL 去重 + 抓取次数限制 + 连续无新信息检测 | toolContext |
| `SearchSessionTracker` | 搜索次数计数器 | toolContext |
| `ReportCompletenessChecker` | 报告完成度检查（工作流模式） | toolContext |
| `ToolBehaviorRegistry` | 工具元数据（deterministic/cacheable） | Spring Bean |

---

## 三、防护机制详解

### 3.1 机制一：分级递进告警（Graduated Escalation）

**解决的问题：** 防止模型在软上限和硬上限之间无限循环。

**设计演进：**
- V1：固定 10 轮软上限 → `counter.set(0)` 重置 → 模型在 10-30 之间无限循环
- V2：5 级信号递进，计数器只增不减

```
轮次:  1 ─────────── 10 ──────── 15 ──────── 20 ──────── 30
信号:  [NONE]      [ADVISORY]  [WARNING]  [CRITICAL]  [FORCE]
行为:  正常执行     注入提示    强烈建议停止  强制停止    强制停止
```

**信号级别定义：**

```java
public enum SignalLevel {
    NONE,       // 无信号，正常执行
    ADVISORY,   // 建议停止：软上限到达、重复调用、URL 重复
    WARNING,    // 强烈建议：信息增益低、连续相同调用
    CRITICAL,   // 即将到达硬上限，强制停止
    FORCE       // 硬上限到达或 fetch 次数超限，立即停止
}
```

**触发条件矩阵：**

| 条件 | 信号级别 | 是否 returnDirect |
|------|---------|-----------------|
| `step >= 10` (softLimit) | ADVISORY | 否 |
| `step >= 15` (escalationWarning) | WARNING | 否 |
| `infoGain == LOW` | WARNING | 否 |
| `repetition == STUCK_IDENTICAL` | WARNING | 否 |
| `step >= 20` (escalationFinal) | CRITICAL | 是 |
| `step >= 30` (hardLimit) | FORCE | 是 |
| `overMaxFetches` | FORCE | 是 |

**关键实现：**

```java
// 计数器只增不移除，counter.set(0) 已移除
int step = counter.incrementAndGet();

// 分级评估
public SignalLevel getLevel() {
    if (currentStep >= hardLimit || overMaxFetches) return SignalLevel.FORCE;
    if (currentStep >= escalationFinal) return SignalLevel.CRITICAL;
    if (currentStep >= escalationWarning || infoGain == LOW || repetition == STUCK_IDENTICAL)
        return SignalLevel.WARNING;
    if (currentStep >= softLimit || repetition == STUCK_SIMILAR || isDuplicateUrl || stuckNoNewInfo)
        return SignalLevel.ADVISORY;
    return SignalLevel.NONE;
}
```

### 3.2 机制二：信息增益检测（Info Gain Detection）

**解决的问题：** 模型反复调用工具但没有获取新信息。

**算法：**

```
输入：最近 N 个工具返回结果的摘要（截取前 500 字符）
处理：滑动窗口内所有两两组合计算加权相似度
输出：avg_similarity > 0.8 → InfoGainLevel.LOW
```

**相似度计算（3 维加权）：**

```java
similarity = 0.5 × jaccard_word_similarity    // 词级 Jaccard
           + 0.3 × numeric_similarity          // 数值集合相似度
           + 0.2 × length_similarity           // 文本长度比
```

| 维度 | 公式 | 为什么需要 |
|------|------|-----------|
| Jaccard 词相似度 | \|A∩B\| / \|A∪B\| | 检测语义重复 |
| 数值相似度 | 数值集合的 Jaccard | 金融场景：相同行情数据 = 无新信息 |
| 长度相似度 | min(len1,len2) / max(len1,len2) | 检测"返回了差不多长的废话" |

**滑动窗口机制：**

```
窗口大小 = 3（可配置）

调用1: [结果A]                    → UNKNOWN（窗口未满）
调用2: [结果A, 结果B]             → UNKNOWN（窗口未满）
调用3: [结果A, 结果B, 结果C]      → HIGH/LOW（计算 3 对相似度）
调用4: [结果B, 结果C, 结果D]      → HIGH/LOW（滑动窗口，丢弃 A）
```

### 3.3 机制三：重复调用检测（Repetition Detection）

**解决的问题：** 模型用相同/相似参数反复调用同一个工具。

**检测策略：**

```java
// 每个工具维护独立的调用历史
Map<String, LinkedList<String>> callHistory;
// key = toolName, value = 最近 threshold 次的归一化参数

// 归一化：去空格、统一空白
private String normalize(String args) {
    return args.trim().replaceAll("\\s+", " ");
}

// 检测逻辑
if (allIdentical)    → STUCK_IDENTICAL  // 完全相同
if (avgSimilarity > 0.8) → STUCK_SIMILAR  // 高度相似
else                 → NONE
```

**示例：**

```
调用1: getUSStockQuote("SNDK")     → 记录
调用2: getUSStockQuote("SNDK")     → 记录
调用3: getUSStockQuote("SNDK")     → STUCK_IDENTICAL → WARNING 信号注入
```

### 3.4 机制四：Fetch 会话追踪（Fetch Session Tracking）

**解决的问题：** 网页抓取工具的特殊循环风险（URL 重复、连续无效抓取）。

**三重防护：**

```
┌─────────────────────────────────────────────────┐
│  1. URL 去重                                      │
│     visitedUrls: Set<String>                      │
│     URL 归一化：lowercase + 去尾部/ + 去 #fragment │
│     重复 URL → 直接跳过，返回提示                   │
│                                                   │
│  2. 抓取次数限制                                   │
│     fetchCount >= maxFetches(3) → FORCE 强制停止    │
│                                                   │
│  3. 连续无新信息检测                                │
│     consecutiveNoNewInfo >=阈值(2) → ADVISORY      │
│     连续 LOW 重置为 0，HIGH 重置计数                 │
└─────────────────────────────────────────────────┘
```

**URL 归一化：**

```java
private String normalizeUrl(String url) {
    return url.trim().toLowerCase()
              .replaceAll("/+$", "")    // 去尾部斜杠
              .replaceAll("#.*$", "");  // 去 fragment
}
// https://example.com/path/ → https://example.com/path
// https://example.com/path#section → https://example.com/path
```

### 3.5 机制五：搜索轮次限制（Search Round Limiting）

**解决的问题：** 模型搜索被拒后换个关键词重试。

**实现：** 简单计数器，`searchCount > maxSearchRounds` 时拒绝并返回强制提示。

```
搜索1: bailian_web_search("闪迪股票")     → 正常执行 (count=1)
搜索2: bailian_web_search("SNDK新闻")     → 超限(2/1), 拒绝
搜索3: bailian_web_search("Sandisk最新")  → 超限(3/1), 拒绝
```

**拒绝消息：** "本次会话搜索次数已达上限。请立即停止所有工具调用，基于已有的搜索结果直接回答用户。"

### 3.6 机制六：报告完成度检查（Report Completeness Check）

**解决的问题：** 工作流模式下，分析师报告已完整但仍继续调用工具"优化"。

**实现：**

```java
public class ReportCompletenessChecker {
    private final int minLength;     // 默认 500 字符
    private final int minSections;   // 默认 2 个 ## 章节

    public boolean isReportSubstantial() {
        String text = accumulatedText.toString();
        if (text.length() < minLength) return false;
        long sectionCount = text.lines()
            .filter(line -> line.trim().startsWith("##"))
            .count();
        return sectionCount >= minSections;
    }
}
```

**工作流集成：** `AnalystNode` 在流式输出过程中实时累积文本，`MaxToolCallManager` 在每次工具调用前检查报告是否已完整。完整则跳过工具调用，直接让模型完成报告。

### 3.7 机制七：工具执行超时（Tool Execution Timeout）

**解决的问题：** 单个工具调用挂起（DNS 超时、服务器无响应）阻塞整个 Agent 循环。

**实现：**

```java
private ToolExecutionResult executeSafely(Prompt prompt, ChatResponse chatResponse) {
    try {
        return CompletableFuture.supplyAsync(
                () -> delegate.executeToolCalls(prompt, chatResponse))
            .orTimeout(toolTimeoutMs, TimeUnit.MILLISECONDS)  // 默认 60 秒
            .join();
    } catch (CompletionException e) {
        if (e.getCause() instanceof TimeoutException) {
            String timeoutMsg = "工具调用超时(" + (toolTimeoutMs/1000) + "秒)，请基于已有信息回答用户。";
            return buildErrorResponse(chatResponse, timeoutMsg);
        }
        // ... 其他异常处理
    }
}
```

### 3.8 机制八：工具调用缓存（Duplicate Call Cache）

**解决的问题：** 相同工具 + 相同参数的重复调用浪费 API 成本。

**实现：** LRU 缓存，最大 50 条，key = `toolName:argumentsHashCode`。

**关键设计决策：** 缓存存储在 `toolContext` 中（而非 `ThreadLocal`），因为 Spring Boot 3.5 使用虚拟线程，`ThreadLocal` 在每次工具调用时可能切换线程，导致缓存失效。

```java
// V1（失效）：ThreadLocal，虚拟线程下每次调用都拿到新实例
private final ThreadLocal<Map<String, List<Message>>> duplicateCache;

// V2（当前）：toolContext，跟 AtomicInteger 等 Tracker 同样方式传递
toolCtx.put(DUPLICATE_CACHE_KEY, new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
        return size() > 50;
    }
});
```

**缓存策略：** 通过 `@ToolBehavior` 注解标记工具是否可缓存：
- `WebFetchTool`：`cacheable = false`（网页内容随时变化）
- `FinancialCalcTool`：`cacheable = true`（相同输入 = 相同输出）
- `FinancialDataTool`：`cacheable = false`（实时行情）

### 3.9 机制九：信号污染隔离（Signal Pollution Isolation）

**解决的问题：** 守护信号被拼接到工具返回结果中，模型将其当作事实性输出。

**V1（已废弃）：**
```java
// 信号拼接到 ToolResponseMessage 的 responseData 末尾
r.responseData() + signal.format()
// 问题：模型把 "已达到硬上限" 当作工具返回的事实
```

**V2（当前）：**
```java
// 信号作为独立的 ToolResponseMessage 注入，工具名 __guard_signal__
List<Message> result = new ArrayList<>(messages.size() + 1);
result.addAll(messages);  // 原始工具结果保持纯净
result.add(new ToolResponseMessage(
    List.of(new ToolResponseMessage.ToolResponse(
        "__guard_signal__", "__guard_signal__", signal.format()))));
return result;
```

**输出清理：** `AgentLoopImpl.sanitizeOutput()` 用正则移除用户可见输出中的信号文本：

```java
private static String sanitizeOutput(String text) {
    return text
        .replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
        .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "")
        .trim();
}
```

### 3.10 机制十：按角色配置防护参数（Per-Role Guard Config）

**解决的问题：** 不同分析师角色需要不同的防护阈值。

**示例配置：**

| 角色 | maxFetches | maxSearchRounds | 说明 |
|------|-----------|-----------------|------|
| MarketAnalyst | 3 | 1 | 技术面分析，主要靠行情接口 |
| FundamentalsAnalyst | 3 | 1 | 基本面分析，主要靠 SQL 查询 |
| NewsAnalyst | 4 | 3 | 新闻分析，需要多次搜索和抓取 |
| SocialAnalyst | 3 | 2 | 舆情分析，需要搜索社交媒体 |
| Trader | 3 | 1 | 交易方案，工具调用较少 |

**实现：** `AgentRole` 枚举携带 `RoleGuardConfig`，`AnalystNode` 创建时注入，优先使用角色配置，回退到全局配置。

---

## 四、状态传递机制

### 4.1 toolContext 传递

Spring AI 的 `AnthropicChatOptions.toolContext()` 是一个 `Map<String, Object>`，在整个工具调用循环中被复用。所有防护状态（计数器、Tracker、缓存）都存储在其中。

```java
AnthropicChatOptions options = AnthropicChatOptions.builder()
    .toolContext(Map.of(
        TOOL_CALL_COUNTER_KEY,      new AtomicInteger(0),
        INFO_GAIN_TRACKER_KEY,      new InfoGainTracker(3, 0.8),
        REPETITION_DETECTOR_KEY,    new RepetitionDetector(3),
        FETCH_SESSION_TRACKER_KEY,  new FetchSessionTracker(3, 2),
        SEARCH_SESSION_TRACKER_KEY, new SearchSessionTracker(1),
        DUPLICATE_CACHE_KEY,        new LinkedHashMap<>(...),
        REPORT_COMPLETENESS_KEY,    new ReportCompletenessChecker(500, 2)
    ))
    .build();
```

### 4.2 为什么不用 ThreadLocal

Spring Boot 3.5 默认使用虚拟线程（Virtual Threads）。虚拟线程在每次 I/O 操作时可能被挂起和恢复到不同的平台线程上，导致 `ThreadLocal` 值丢失。

```
工具调用1 → 虚拟线程A → ThreadLocal 初始化
  ↓ (I/O 等待，虚拟线程挂起)
工具调用2 → 虚拟线程B → ThreadLocal 重新初始化（丢失之前的状态）
```

而 `toolContext` 是通过 `Prompt` 对象传递的，不依赖线程模型。

---

## 五、配置参数

```yaml
agent:
  tool:
    # 迭代控制
    max-iterations: 30           # 硬上限（FORCE，returnDirect=true）
    soft-limit: 10               # 软上限（ADVISORY，开始注入信号）
    escalation-warning: 15       # 升级警告（WARNING）
    escalation-final: 20         # 最终警告（CRITICAL，returnDirect=true）

    # 信息增益检测
    info-gain-window: 3          # 滑动窗口大小
    info-gain-threshold: 0.8     # 相似度阈值，超过视为无新信息

    # 重复调用检测
    repetition-threshold: 3      # 连续相似调用次数阈值

    # Fetch 防护
    max-fetches: 3               # 单会话最大抓取次数
    max-consecutive-no-new-info: 2  # 连续无新信息抓取次数

    # Search 防护
    max-search-rounds: 1         # 单会话最大搜索次数

    # 超时控制
    tool-timeout-seconds: 60     # 单个工具调用超时

    # 报告完成度（工作流模式）
    report-min-length: 500       # 报告最小字符数
    report-min-sections: 2       # 报告最少章节数
```

---

## 六、防护信号格式

```text
[GUARD: WARNING]
Action: 检测到重复调用相同工具和参数，请换一种方式或基于已有结果回答。
Context: step=8/30, repeat=stuck_identical
[/GUARD]
```

信号格式设计原则：
- **独立消息注入**，不污染工具输出
- **行动导向**，每个级别只给一条明确指令
- **紧凑上下文**，只包含模型做决策需要的最少信息
- **用户不可见**，`sanitizeOutput()` 在输出时自动移除

---

## 七、设计决策与权衡

### 7.1 软停止 vs 硬停止

| 方案 | 优点 | 缺点 |
|------|------|------|
| 硬停止（V1） | 简单确定 | 可能误杀复杂任务 |
| 软停止 + 自反思（V2） | 模型自行判断 | 可能忽略信号 |
| **分级递进（V3，当前）** | 兼顾灵活性和确定性 | 需要调参 |

### 7.2 信号注入方式

| 方案 | 优点 | 缺点 |
|------|------|------|
| 拼接到 tool result（V1） | 实现简单 | 污染事实，模型可能忽略 |
| DeveloperMessage（理想） | 语义清晰 | Anthropic API 不支持 |
| **独立 ToolResponseMessage（V2，当前）** | 兼容性好，可区分 | 需要 sanitize |

### 7.3 相似度算法

| 方案 | 优点 | 缺点 |
|------|------|------|
| 纯文本 Jaccard | 简单快速 | 忽略数值变化 |
| Embedding 相似度 | 语义准确 | 需要额外 API 调用 |
| **加权多维（当前）** | 兼顾词义和数值 | 权重需要调参 |

---

## 八、运行时日志示例

```
# 正常工具调用
[MaxToolCallManager] 工具调用轮次: 1/30, 工具: [getUSStockQuote]
[MaxToolCallManager] 信号: level=NONE, infoGain=UNKNOWN, repetition=NONE, shouldInject=false

# 信息增益检测
[MaxToolCallManager] 工具调用轮次: 3/30, 工具: [fetchArticleContent]
[MaxToolCallManager] Fetch tracking: count=1, dup=false, consecNoNew=0, overMax=false
[MaxToolCallManager] 信号: level=NONE, infoGain=HIGH, repetition=NONE, shouldInject=false

# 重复调用检测
[MaxToolCallManager] 工具调用轮次: 8/30, 工具: [getUSStockQuote]
[MaxToolCallManager] 信号: level=WARNING, infoGain=HIGH, repetition=STUCK_IDENTICAL, shouldInject=true

# 搜索限制
[MaxToolCallManager] 工具调用轮次: 5/30, 工具: [bailian_web_search]
[MaxToolCallManager] 搜索次数超限(2/1), 拒绝搜索

# Fetch 硬上限
[MaxToolCallManager] Fetch tracking: count=3, dup=false, consecNoNew=0, overMax=true
[MaxToolCallManager] 信号: level=FORCE, infoGain=HIGH, repetition=NONE, shouldInject=true
[MaxToolCallManager] 达到 fetch 硬上限 (fetchCount=3), 强制停止

# URL 去重
[MaxToolCallManager] 检测到重复URL，跳过抓取: https://example.com/article

# 工具执行超时
[MaxToolCallManager] 工具执行超时 (60000ms)
```

---

## 九、文件清单

| 文件 | 职责 |
|------|------|
| `MaxToolCallManager.java` | 核心防护管理器，包装 ToolCallingManager |
| `GuardSignal.java` | 分级信号评估，格式化输出 |
| `InfoGainTracker.java` | 信息增益检测（滑动窗口 + 相似度） |
| `RepetitionDetector.java` | 重复调用检测 |
| `FetchSessionTracker.java` | Fetch 会话追踪（URL 去重 + 次数限制） |
| `SearchSessionTracker.java` | Search 轮次限制 |
| `ReportCompletenessChecker.java` | 报告完成度检查（工作流模式） |
| `SimilarityUtils.java` | 加权多维相似度计算 |
| `ToolBehaviorRegistry.java` | 工具元数据注册（deterministic/cacheable） |
| `ToolBehavior.java` | 工具行为注解 |
| `ToolGuardProperties.java` | 配置参数 |
| `AgentLoopImpl.java` | 单轮对话入口，创建 toolContext |
| `AnalystNode.java` | 工作流分析师节点，创建 toolContext |
| `TraderNode.java` | 工作流交易员节点 |
| `AgentRole.java` | 角色定义 + 按角色防护配置 |
| `WorkflowAgentFactory.java` | 工作流节点工厂 |
