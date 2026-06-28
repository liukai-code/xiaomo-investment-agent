# 工具调用守护机制 V2 改进方案

## 一、现状评估

### 当前设计（V1）

```
轮次计数 → 达到 10 轮 → 在 tool result 末尾追加提示 → 计数器归零 → 模型自行判断
```

### V1 优点

| 优点 | 说明 |
|------|------|
| 软上限机制 | 比硬 kill 优雅，属于 Soft budget + self-reflection stopping |
| 重复调用缓存 | 同名+同参数返回缓存，避免 API 成本爆炸和 retry loop |
| 允许继续循环 | 不是一刀切，模型可基于提示判断是否继续 |

### V1 关键问题

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 1 | 阈值盲设 | 高 | 固定 10 轮不区分任务复杂度，简单任务浪费 token，复杂任务被干扰推理 |
| 2 | 污染 tool result | 高 | 在 tool output 末尾追加系统提示，模型会将其当作工具输出的事实部分，可能导致 hallucination 或忽略 |
| 3 | 缺少任务完成信号 | 高 | 只有轮次控制，没有"是否完成任务"的显式信号。stop condition 应该是 task solved / no new info，而非 iteration limit |
| 4 | 缓存策略不完整 | 中 | 同名+同参数不够：非确定性 API（行情/时间）参数相同但结果不同；上下文不同语义不同 |

**一句话总结：在用"轮次数"控制 agent，但真正应该控制的是"信息增益"。**

---

## 二、V2 设计目标

```
stop condition = task_solved OR no_new_information OR max_hard_limit_reached
```

核心原则：让模型自己判断何时停止，但提供足够的信号支撑其判断。

---

## 三、V2 架构设计

### 3.1 双停止机制

```
工具调用循环
  │
  ├─ 每轮执行后检查：
  │   ├─ 条件 A：模型主动返回 text（无 tool_use）→ 任务完成，自然停止
  │   ├─ 条件 B：连续 N 轮无新信息增益 → 强制提示模型评估
  │   └─ 条件 C：达到硬上限（如 30 轮）→ 强制停止，返回已有结果
  │
  └─ 软提示区间（10-30 轮）：注入观察信号，不中断循环
```

| 条件 | 触发时机 | 行为 |
|------|---------|------|
| A. task_complete | 模型返回纯文本，无 tool_use | 自然停止，正常返回 |
| B. no_new_info | 连续 3 轮工具调用结果高度相似 | 注入 developer message 提示模型评估 |
| C. hard_limit | 累计 30 轮 | 强制 returnDirect=true，停止循环 |

### 3.2 信号注入方式（不污染 tool result）

**方案 A：Developer Message 注入（推荐）**

通过 `ToolExecutionResult.conversationHistory()` 在 tool result 之后追加一条独立的 DeveloperMessage：

```
conversationHistory = [
    ToolResponseMessage(工具实际结果),        ← 纯净的工具输出
    DeveloperMessage(观察信号)                ← 独立的控制信号
]
```

观察信号内容：

```
[Tool Controller] Step 10/30
- completion_estimate: 0.6
- info_gain_last_3_steps: low (结果相似度 > 0.8)
- suggestion: 基于已有数据尝试回答，或调用不同工具获取新维度信息
```

**优势：** 模型能区分"事实"和"控制信号"，不会 hallucination。

**方案 B：结构化元数据（进阶，需要模型支持）**

```json
{
  "result": "工具实际输出...",
  "_meta": {
    "step": 10,
    "max_steps": 30,
    "info_gain": "low",
    "hint": "consider stopping"
  }
}
```

当前 mimo-v2.5-pro 不一定支持，留作未来优化。

### 3.3 信息增益检测

**目标：** 判断最近 N 轮工具调用是否产生了新信息。

**实现策略：**

```java
class InfoGainTracker {
    // 最近 N 轮工具结果的摘要
    private final LinkedList<String> recentSummaries = new LinkedList<>();
    private static final int WINDOW_SIZE = 3;
    private static final double SIMILARITY_THRESHOLD = 0.8;

    void record(String toolResultSummary) {
        recentSummaries.addLast(toolResultSummary);
        if (recentSummaries.size() > WINDOW_SIZE) {
            recentSummaries.removeFirst();
        }
    }

    InfoGainLevel evaluate() {
        if (recentSummaries.size() < WINDOW_SIZE) {
            return InfoGainLevel.UNKNOWN;
        }
        // 计算最近 N 轮结果的相似度
        double similarity = computeAverageSimilarity(recentSummaries);
        if (similarity > SIMILARITY_THRESHOLD) {
            return InfoGainLevel.LOW;  // 结果高度相似，信息增益低
        }
        return InfoGainLevel.HIGH;
    }
}
```

**相似度计算（轻量级）：**

- 方案 1：结果文本的 Jaccard 相似度（词级）
- 方案 2：结果长度 + 关键数字的变化检测
- 方案 3：提取结果中的数值，比较数值集合是否变化

推荐方案 2，足够轻量且对金融计算场景有效。

### 3.4 缓存策略升级

**V1：** `hash(toolName + arguments)`

**V2：** 分层缓存

| 层级 | Key | 适用场景 |
|------|-----|---------|
| 精确缓存 | `hash(name + args)` | 完全相同的调用（SQL 查询、计算公式） |
| 语义缓存 | `hash(name + normalized_args)` | 参数格式不同但语义相同（如 "10000" vs "1万"） |
| 不缓存 | 标记为 non-cacheable | 非确定性工具（行情查询、时间相关、随机性） |

**工具分类标记：**

```java
public interface ToolMetadata {
    boolean isDeterministic();      // 相同输入是否总是相同输出
    boolean isCacheable();          // 是否允许缓存
    int getCacheTTLSeconds();       // 缓存有效期
}
```

在 `@Tool` 注解或工具注册时声明：

```java
@Tool(description = "获取A股实时行情", deterministic = false, cacheable = false)
public String getAShareQuote(String stockCode) { ... }

@@Tool(description = "复利计算", deterministic = true, cacheable = true)
public String compoundInterest(String principal, String rate, String years) { ... }
```

### 3.5 重复调用惩罚

**V1：** 同名+同参数 → 返回缓存（被动）

**V2：** 检测"无进展循环" → 主动提示模型

```java
class RepetitionDetector {
    // key: toolName, value: 最近调用的参数列表
    private final Map<String, LinkedList<String>> callHistory = new HashMap<>();
    private static final int REPETITION_THRESHOLD = 3;

    boolean isStuck(String toolName, String args) {
        LinkedList<String> history = callHistory.computeIfAbsent(
            toolName, k -> new LinkedList<>());
        history.addLast(args);
        if (history.size() > REPETITION_THRESHOLD) {
            history.removeFirst();
        }

        if (history.size() < REPETITION_THRESHOLD) {
            return false;
        }

        // 检查最近 N 次调用是否参数完全相同或高度相似
        return allSimilar(history);
    }
}
```

当检测到"卡住"时，注入 Developer Message：

```
[Tool Controller] 检测到你已连续 3 次调用 executeQuery 且参数相似。
请评估：1) 是否需要换一种查询方式？2) 是否已有足够信息回答用户？
```

---

## 四、V2 实现计划

### Phase 1：信号注入重构（优先级最高）

**改动范围：** `MaxToolCallManager.java`

- [ ] 移除 tool result 追加提示的方式
- [ ] 实现 `DeveloperMessage` 注入机制
- [ ] 观察信号包含：当前轮次、硬上限、建议

**预期效果：** 模型不再把控制信号当作事实，推理质量提升。

### Phase 2：信息增益检测

**改动范围：** 新增 `InfoGainTracker.java`

- [ ] 实现轻量级文本相似度计算
- [ ] 滑动窗口跟踪最近 N 轮结果
- [ ] 低信息增益时触发 Developer Message 提示

**预期效果：** 模型能感知"最近几轮没获取到新信息"，主动收敛。

### Phase 3：缓存策略升级

**改动范围：** `MaxToolCallManager.java` + 各 Tool 类

- [ ] 工具元数据接口（deterministic / cacheable 标记）
- [ ] 非确定性工具跳过缓存
- [ ] 参数归一化（"1万" → "10000"）

**预期效果：** 行情类工具不再返回过时缓存，计算类工具缓存命中率提升。

### Phase 4：重复调用惩罚

**改动范围：** 新增 `RepetitionDetector.java`

- [ ] 检测同工具连续相似调用
- [ ] 卡住时注入强制评估提示
- [ ] 与信息增益检测联动

**预期效果：** 模型陷入死循环时被主动拉出。

---

## 五、配置参数

```yaml
agent:
  tool:
    max-iterations: 30          # 硬上限（returnDirect=true 强制停止）
    soft-limit: 10              # 软上限（开始注入观察信号）
    info-gain-window: 3         # 信息增益检测窗口大小
    info-gain-threshold: 0.8    # 相似度阈值，超过视为无新信息
    repetition-threshold: 3     # 重复调用检测阈值
```

---

## 六、V1 → V2 迁移影响

| 组件 | V1 行为 | V2 行为 | 兼容性 |
|------|---------|---------|--------|
| `MaxToolCallManager` | 追加提示到 tool result | Developer Message 注入 | 改造 |
| `AgentLoopImpl` | 无变化 | 无变化 | 不变 |
| 各 Tool 类 | 无变化 | 需添加元数据标记（可选） | 向后兼容 |
| 配置文件 | `max-iterations: 10` | 新增多个参数 | 向后兼容 |
| `ToolCallLimitExceededException` | 已废弃 | 可删除 | 无影响 |
