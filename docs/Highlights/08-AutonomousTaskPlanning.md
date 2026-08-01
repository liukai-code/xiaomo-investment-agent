# 自主任务规划 -- LLM 如何为自己制定执行计划

> 本文档是小墨项目技术亮点系列的第 8 篇，面向初次接触项目的开发者，从问题出发，逐步拆解 TaskPlanner 自主任务规划的设计思路与实现细节。

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

- 理解为什么复杂请求不能直接丢给 LLM 自由发挥，需要结构化的执行计划
- 掌握 `TaskPlanner` 如何用规则判断执行模式（DIRECT / PARALLEL / PLANNING），用 LLM 生成计划
- 理解 `PlanContext` 如何注入 System Prompt 引导 LLM 按步骤执行
- 了解 `Scratchpad` 如何累积中间结果供 LLM 最终汇总
- 知道执行计划如何通过 SSE 推送到前端可视化展示

---

## 二、为什么需要这个设计

### 2.1 问题场景

用户说"分析茅台的估值和资金面，给出买入建议"。这需要：
1. 查实时行情（获取 PE/PB）
2. 查研报（获取机构目标价）
3. 查融资融券（获取杠杆资金）
4. 查北向资金（获取外资动向）
5. 综合以上数据，计算估值
6. 给出买入建议

如果让 LLM 自由发挥，它可能：
- 跳过某些步骤（"我觉得不用查北向资金"）
- 重复调用同一工具（"再查一次行情确认一下"）
- 顺序混乱（先给建议再查数据）
- 遗漏关键数据（"忘了查研报"）

### 2.2 不这样做的后果

| 场景 | 无规划 | 有规划 |
|------|--------|--------|
| 复杂分析 | LLM 自由发挥，步骤不可控 | LLM 按计划执行，步骤可追踪 |
| 工具选择 | 可能选错工具或遗漏 | 计划明确每步用什么工具 |
| 中间结果 | 逐步累积到上下文，token 膨胀 | Scratchpad 压缩存储，节省 token |
| 执行透明度 | 用户不知道 Agent 在干什么 | 前端实时展示执行计划和进度 |

### 2.3 设计目标

1. **规则判断 + LLM 规划**：执行模式用规则判断（快），具体计划用 LLM 生成（准）
2. **计划注入引导**：将计划作为 System Prompt 注入，引导 LLM 按步骤执行
3. **中间结果暂存**：Scratchpad 压缩存储每步结果，避免上下文膨胀
4. **前端可视化**：执行计划实时推送到前端，用户可追踪进度

---

## 三、整体架构

### 3.1 一句话描述

对复杂请求，先用规则判断是否需要规划（PLANNING 模式），再调用 LLM 生成结构化执行计划（JSON），将计划注入 System Prompt 引导 Agent 按步骤执行，每步结果存入 Scratchpad 供最终汇总。

### 3.2 架构图

```mermaid
flowchart TD
    A[用户消息] --> B{规则判断执行模式}
    B -->|DIRECT| C[直接执行，无计划]
    B -->|PARALLEL| D[并行执行，无计划]
    B -->|PLANNING| E[调用 LLM 生成计划]

    E --> F[TaskPlanner.plan()]
    F --> G[LLM 输出 JSON 计划]
    G --> H[PlanContext]

    H --> I[注入 System Prompt]
    I --> J[Agent 按计划执行]

    J --> K[步骤1: 调用工具]
    K --> L[Scratchpad 记录结果]
    L --> M[步骤2: 调用工具]
    M --> N[Scratchpad 记录结果]
    N --> O[...]
    O --> P[最后一步: 综合汇总]

    H --> Q[SSE 推送到前端]
    Q --> R[WorkflowPanel 展示计划]

    style E fill:#fff3e0
    style H fill:#e3f2fd
    style L fill:#e8f5e9
```

### 3.3 核心组件表

| 组件 | 文件路径 | 职责 |
|------|---------|------|
| TaskPlanner | `agent/service/impl/TaskPlanner.java` | 执行模式判断 + LLM 计划生成 |
| PlanContext | `agent/service/impl/PlanContext.java` | 执行计划数据模型 |
| Scratchpad | `agent/service/impl/Scratchpad.java` | 中间结果暂存器 |
| PlanningProperties | `agent/config/PlanningProperties.java` | 规划参数配置 |
| RequestFeatures | `agent/intent/RequestFeatures.java` | 从用户消息提取的结构化特征 |
| AgentLoopImpl | `agent/service/impl/AgentLoopImpl.java` | 中央编排（调用规划、注入计划） |
| ContextBuilder | `agent/service/impl/ContextBuilder.java` | 将计划注入 System Prompt |
| ChatStreamEvent | `agent/service/ChatStreamEvent.java` | SSE 事件（PLAN 类型） |

---

## 四、代码走读

### 4.1 执行模式判断：三层决策

`TaskPlanner.determineExecutionMode()` 用规则（不走 LLM）判断执行模式：

```java
// TaskPlanner.java — determineExecutionMode() 精简版
public ExecutionMode determineExecutionMode(String message, IntentType intent, AnalysisDepth depth) {
    // 深度分析始终触发 PLANNING
    if (depth == AnalysisDepth.DEEP) return ExecutionMode.PLANNING;

    RequestFeatures features = extractFeatures(message);

    // 存在前后依赖 → PLANNING（"先...再..."）
    if (features.hasDependentSteps()) return ExecutionMode.PLANNING;

    // 多标的 × 多维度 → PLANNING
    if (features.targetCount() >= 2 && features.dimensionCount() >= 2) return ExecutionMode.PLANNING;

    // 多维度 + 综合决策需求 → PLANNING
    if (features.dimensionCount() >= 2 && features.hasSynthesisRequirement()) return ExecutionMode.PLANNING;

    // 3 个以上子目标 → PLANNING
    if (features.subGoalCount() >= 3) return ExecutionMode.PLANNING;

    // 预估 2 次以上工具调用 → PARALLEL
    if (features.estimatedToolCalls() >= 2) return ExecutionMode.PARALLEL;

    return ExecutionMode.DIRECT;
}
```

**三种模式的区别**：

| 模式 | 触发条件 | 工具策略 | 是否生成计划 |
|------|---------|---------|------------|
| DIRECT | 简单单步请求 | 静态白名单 | 否 |
| PARALLEL | 多工具但无依赖 | 静态白名单 | 否 |
| PLANNING | 复杂多步、有依赖、深度分析 | PLANNER_MANAGED（全量工具） | 是 |

### 4.2 LLM 计划生成：TaskPlanner.plan()

当执行模式为 PLANNING 时，调用 LLM 生成结构化执行计划：

```java
// TaskPlanner.java — plan() 精简版
public PlanContext plan(String message, IntentType intent,
                        IntentResult.ResolvedTarget target, Set<String> availableToolNames) {
    String targetHint = target != null ? "当前分析标的：" + target.name() + "（" + target.code() + "）" : "";

    String prompt = String.format(PLAN_PROMPT_TEMPLATE, TOOL_CATALOG, message, targetHint, maxSteps);

    ChatResponse response = chatClient.prompt()
            .user(prompt)
            .options(AnthropicChatOptions.builder()
                    .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                    .maxTokens(planMaxTokens)
                    .temperature(0.3)  // 低温度保证计划稳定性
                    .build())
            .call()
            .chatResponse();

    return parsePlan(response.getResult().getOutput().getText(), message);
}
```

**Prompt 设计要点**：
- 提供完整的工具目录（TOOL_CATALOG），让 LLM 知道有哪些工具可用
- 注入标的信息，确保计划围绕目标标的
- 限制步骤数（maxSteps，默认 5）
- 要求输出严格 JSON 格式

**LLM 输出示例**：

```json
{
  "goal": "综合分析茅台的估值和资金面，给出买入建议",
  "steps": [
    {"id": 1, "action": "获取实时行情（PE/PB/市值）", "tool": "a_stock_quote", "args_hint": "operation=tencentQuote"},
    {"id": 2, "action": "获取机构研报和目标价", "tool": "a_stock_report", "args_hint": "operation=stockReport"},
    {"id": 3, "action": "获取融资融券数据", "tool": "a_stock_capital", "args_hint": "operation=marginTrading"},
    {"id": 4, "action": "获取北向资金数据", "tool": "a_stock_capital", "args_hint": "operation=northboundFlow"},
    {"id": 5, "action": "综合以上数据，生成分析报告和买入建议", "tool": "", "args_hint": ""}
  ]
}
```

### 4.3 计划注入：System Prompt 引导

`ContextBuilder.buildContext()` 将计划格式化后注入 System Prompt：

```
[执行计划]
目标：综合分析茅台的估值和资金面，给出买入建议

步骤 1: 获取实时行情（PE/PB/市值） → a_stock_quote（operation=tencentQuote）
步骤 2: 获取机构研报和目标价 → a_stock_report（operation=stockReport）
步骤 3: 获取融资融券数据 → a_stock_capital（operation=marginTrading）
步骤 4: 获取北向资金数据 → a_stock_capital（operation=northboundFlow）
步骤 5: 综合以上数据，生成分析报告和买入建议

请按步骤顺序执行工具调用，每步完成后记录关键数据，最后汇总生成完整报告。
```

**为什么注入 System Prompt 而不是直接控制工具调用？**

因为 Spring AI 的工具调用是由 LLM 自主决定的。注入 System Prompt 是最自然的引导方式 — LLM 看到计划后会自觉按步骤执行。配合 `MaxToolCallManager` 的标的锁定和重复检测，形成"软引导 + 硬约束"的双重保障。

### 4.4 中间结果暂存：Scratchpad

多步执行过程中，每步工具调用的结果会存入 `Scratchpad`：

```java
// Scratchpad.java — 核心逻辑
public class Scratchpad {
    private final List<StepResult> results = new ArrayList<>();
    private final int maxLength;  // 每步摘要最大长度，默认 200 字符

    public void record(int stepId, String toolName, String rawResult) {
        String summary = truncate(rawResult, maxLength);  // 压缩存储
        results.add(new StepResult(stepId, toolName, summary));
    }

    public String format() {
        StringBuilder sb = new StringBuilder("## 已完成步骤的结果摘要\n");
        for (StepResult r : results) {
            sb.append("[步骤").append(r.stepId).append("] ").append(r.toolName)
              .append(": ").append(r.summary).append("\n");
        }
        return sb.toString();
    }
}
```

**Scratchpad 的作用**：

每步工具调用后，`MaxToolCallManager` 将结果追加到 Scratchpad，然后注入到 System Prompt 中：

```java
// MaxToolCallManager.java — 注入 Scratchpad
Scratchpad scratchpad = extractFromContext(prompt, SCRATCHPAD_KEY, Scratchpad.class);
if (scratchpad != null) {
    scratchpad.record(step, toolName, resultText);
    injectScratchpadToSystemMessage(prompt, scratchpad);
}
```

这样 LLM 在执行下一步时，能看到所有已完成步骤的摘要，避免重复查询。

### 4.5 前端可视化

执行计划通过 SSE 的 `PLAN` 事件推送到前端：

```java
// AgentLoopImpl.java — 发射计划事件
if (planContext != null) {
    List<ChatStreamEvent.PlanStepDto> planStepDtos = planContext.steps().stream()
            .map(s -> new ChatStreamEvent.PlanStepDto(s.id(), s.action(), s.tool()))
            .toList();
    statusSink.tryEmitNext(ChatStreamEvent.plan(planContext.goal(), planStepDtos));
}
```

前端 `WorkflowPanel` 组件接收 `PLAN` 事件后展示执行计划面板，随着工具调用的推进（`TOOL_CALL` / `TOOL_RESULT` 事件），实时更新每步的状态（待执行 / 执行中 / 已完成）。

---

## 五、配置与调参

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `agent.planning.enabled` | `true` | 任务规划器总开关 |
| `agent.planning.maxSteps` | `5` | LLM 生成执行计划的最大步骤数 |
| `agent.planning.planMaxTokens` | `1024` | LLM 生成计划的 max_tokens |
| `agent.planning.scratchpadMaxLength` | `200` | Scratchpad 每步摘要最大字符数 |

---

## 六、实战案例

### 6.1 PLANNING 模式："分析茅台的估值和资金面，给出买入建议"

```
Step 1: 规则判断
  → hasAnalysisIntent=true, dimensionCount=2 (估值+资金面), hasSynthesisRequirement=true
  → ExecutionMode.PLANNING

Step 2: LLM 生成计划
  → TaskPlanner.plan() 调用 LLM (temperature=0.3)
  → 输出: {goal, steps[5步]}

Step 3: 注入 System Prompt
  → [执行计划] 注入到 System Prompt

Step 4: 按计划执行
  → 步骤1: a_stock_quote(tencentQuote) → Scratchpad 记录
  → 步骤2: a_stock_report(stockReport) → Scratchpad 记录
  → 步骤3: a_stock_capital(marginTrading) → Scratchpad 记录
  → 步骤4: a_stock_capital(northboundFlow) → Scratchpad 记录
  → 步骤5: 综合汇总，生成报告

Step 5: 前端展示
  → SSE 推送 PLAN 事件 → WorkflowPanel 显示执行计划
  → 每步 TOOL_CALL/TOOL_RESULT 事件 → 实时更新进度
```

### 6.2 PARALLEL 模式："茅台和五粮液的行情"

```
Step 1: 规则判断
  → targetCount=1 (只解析出一个标的), dimensionCount=1, estimatedToolCalls=1
  → ExecutionMode.DIRECT

→ 无计划，直接调用 a_stock_quote(tencentQuote, stockCodes="600519,000858")
```

### 6.3 DIRECT 模式："茅台多少钱"

```
Step 1: 规则判断
  → dimensionCount=0, estimatedToolCalls=1
  → ExecutionMode.DIRECT

→ 无计划，直接调用 a_stock_quote(tencentQuote, stockCodes="600519")
```

---

## 七、与其他模块的关系

```mermaid
flowchart LR
    IC[意图分类器] -->|ExecutionMode| Agent[AgentLoopImpl]
    Agent -->|PLANNING 模式| TP[TaskPlanner]
    TP -->|PlanContext| CB[ContextBuilder]
    CB -->|注入 System Prompt| SCC[Spring AI ChatClient]

    Agent -->|创建| SP[Scratchpad]
    MTM[MaxToolCallManager] -->|record()| SP
    MTM -->|injectScratchpadToSystemMessage()| SCC

    TP -->|PLAN 事件| SSE[SSE Stream]
    SSE -->|前端| WFP[WorkflowPanel]

    style TP fill:#fff3e0
    style SP fill:#e8f5e9
```

修改任务规划系统时需要注意的联动点：
- 修改 `determineExecutionMode()` 的规则 → 影响哪些请求进入 PLANNING 模式
- 修改 `PLAN_PROMPT_TEMPLATE` → 影响 LLM 生成的计划质量
- 修改 `Scratchpad` 的 maxLength → 影响中间结果的详细程度和 token 消耗
- 新增工具 → 同步更新 `TOOL_CATALOG`，让 TaskPlanner 知道新工具的存在

---

## 八、常见问题排查

| 现象 | 可能原因 | 排查方法 |
|------|---------|---------|
| 复杂请求没有生成计划 | `agent.planning.enabled=false` 或规则未触发 PLANNING | 检查配置和日志 `[IntentClassifier] mode=` |
| 计划步骤过多/过少 | `maxSteps` 设置不当 | 调整 `agent.planning.maxSteps` |
| LLM 不按计划执行 | 计划注入失败或 System Prompt 被覆盖 | 检查 `ContextBuilder.buildContext()` 日志 |
| Scratchpad 结果为空 | `scratchpadMaxLength` 过小 | 增大配置值 |
| 前端不显示计划 | SSE PLAN 事件未推送 | 检查 `statusSink` 是否创建 |
| 计划 LLM 调用失败 | API Key 无效或网络异常 | 检查 `[TaskPlanner] 规划 LLM 调用失败` 日志 |

---

## 九、源码索引

| 文件 | 路径 | 关键方法 |
|------|------|---------|
| TaskPlanner | `agent/service/impl/TaskPlanner.java` | `determineExecutionMode()`, `plan()`, `parsePlan()`, `extractFeatures()` |
| PlanContext | `agent/service/impl/PlanContext.java` | 计划数据模型 |
| Scratchpad | `agent/service/impl/Scratchpad.java` | `record()`, `format()` |
| PlanningProperties | `agent/config/PlanningProperties.java` | 配置参数 |
| RequestFeatures | `agent/intent/RequestFeatures.java` | 特征提取结果 |
| ExecutionMode | `agent/intent/ExecutionMode.java` | DIRECT / PARALLEL / PLANNING |
| AgentLoopImpl | `agent/service/impl/AgentLoopImpl.java` | `maybePlan()` |
| ContextBuilder | `agent/service/impl/ContextBuilder.java` | 计划注入 System Prompt |
| MaxToolCallManager | `agent/service/impl/MaxToolCallManager.java` | Scratchpad 记录和注入 |
| ChatStreamEvent | `agent/service/ChatStreamEvent.java` | `plan()` 事件构造 |

---

## 十、延伸阅读

- [意图分类 + 工具过滤](03-IntentClassificationAndToolFiltering.md) — ExecutionMode 的判断来源
- [工具调用防护](02-ToolGuardSystem.md) — Scratchpad 与 MaxToolCallManager 的协作
- [多智能体深度分析工作流](01-MultiAgentWorkflow.md) — PLANNING 模式与深度分析工作流的区别
- [SSE 流式架构](06-SSEStreamingAndNotification.md) — PLAN 事件的前端展示
