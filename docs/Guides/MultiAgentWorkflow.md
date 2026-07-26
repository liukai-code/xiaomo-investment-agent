# 多智能体深度分析工作流 — 技术详解

> 本文档详细记录了基于 Spring Boot + Spring AI 构建的多智能体工作流系统的架构设计、核心实现和技术亮点。

---

## 目录

1. [系统架构总览](#1-系统架构总览)
2. [核心设计思想](#2-核心设计思想)
3. [四层流水线详解](#3-四层流水线详解)
4. [工作流引擎设计](#4-工作流引擎设计)
5. [Agent 节点实现](#5-agent-节点实现)
6. [状态管理与线程安全](#6-状态管理与线程安全)
7. [SSE 流式输出协议](#7-sse-流式输出协议)
8. [工具隔离与复用](#8-工具隔离与复用)
9. [守卫系统集成](#9-守卫系统集成)
10. [持久化与配置](#10-持久化与配置)
11. [前端架构](#11-前端架构)
12. [技术亮点总结（简历素材）](#12-技术亮点总结简历素材)
13. [与 LangGraph 的对比](#13-与-langgraph-的对比)

---

## 1. 系统架构总览

### 1.1 整体架构图

```
用户输入 "全面分析美光科技"
        │
        ▼
┌─────────────────────────────────────────────────────┐
│                 AgentLoopController                   │
│  GET /agent/chat/deep-analysis?message=...           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              DeepAnalysisWorkflow                     │
│  顶层编排器，构建 WorkflowGraph 并执行                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                WorkflowEngine                         │
│  Flux.concatMap 按序执行各层节点                     │
└──────────────────────┬──────────────────────────────┘
                       │
    ┌──────────────────┼──────────────────┬────────────┐
    ▼                  ▼                  ▼            ▼
┌────────┐      ┌───────────┐      ┌─────────┐  ┌──────────┐
│Layer 1 │      │ Layer 2   │      │ Layer 3 │  │ Layer 4  │
│并行采集│─────▶│ 多空辩论  │─────▶│ 交易决策│──▶│ 风险评估 │
│3个Agent│      │ Bull vs   │      │ Trader  │  │ 三方辩论 │
│Flux    │      │ Bear +    │      │ +工具   │  │ +裁决    │
│.merge()│      │ Manager   │      │         │  │          │
└────────┘      └───────────┘      └─────────┘  └──────────┘
    │                │                  │            │
    │ 并行           │ 串行             │ 串行       │ 串行
    ▼                ▼                  ▼            ▼
┌────────┐      ┌───────────┐      ┌─────────┐  ┌──────────┐
│Market  │      │Bull辩论   │      │制定交易 │  │激进/保守 │
│Fund    │      │Bear辩论   │      │方案     │  │中立辩论  │
│News    │      │×N轮交替   │      │         │  │+最终裁决 │
│Analyst │      │→Manager   │      │         │  │→JSON输出 │
└────────┘      └───────────┘      └─────────┘  └──────────┘
```

### 1.2 包结构

```
com.xiaomo.agent.workflow/
├── engine/                          # 工作流引擎
│   ├── WorkflowNode.java           # 节点接口
│   ├── WorkflowEdge.java           # 边定义（条件转移）
│   ├── WorkflowGraph.java          # 有向图构建器
│   └── WorkflowEngine.java         # 图执行引擎
│
├── node/                            # 具体节点实现
│   ├── AnalystNode.java            # 分析师节点（带工具）
│   ├── ParallelFanOutNode.java     # 并行扇出节点
│   ├── DebateNode.java             # 辩论节点
│   ├── JudgeNode.java              # 裁决节点
│   ├── TraderNode.java             # 交易员节点（带工具）
│   ├── BullBearDebateOrchestrator.java  # 多空辩论编排
│   └── RiskDebateOrchestrator.java      # 风险辩论编排
│
├── state/                           # 共享状态
│   ├── WorkflowState.java          # 核心状态（线程安全）
│   ├── AgentReport.java            # 分析师报告
│   ├── DebateMessage.java          # 辩论消息
│   └── FinalDecision.java          # 最终决策
│
├── event/                           # SSE 事件模型
│   ├── WorkflowEvent.java          # 事件记录
│   └── WorkflowEventType.java      # 事件类型枚举
│
├── agent/                           # Agent 角色系统
│   ├── AgentRole.java              # 11 个角色枚举
│   └── WorkflowAgentFactory.java   # Agent 工厂
│
├── config/                          # 配置
│   └── WorkflowProperties.java     # 工作流配置属性
│
├── persist/                         # 持久化
│   ├── WorkflowAnalysis.java       # JPA 实体
│   └── WorkflowAnalysisRepository.java
│
└── service/                         # 业务服务
    └── DeepAnalysisWorkflow.java   # 顶层编排器
```

---

## 2. 核心设计思想

### 2.1 为什么不用 LangGraph？

LangGraph 是 Python 生态的多智能体框架，基于有向图建模。本项目是 Java/Spring Boot 技术栈，直接引入 LangGraph 不现实。但核心思想可以借鉴：

| LangGraph 概念 | 本项目实现 | 差异 |
|---------------|-----------|------|
| `StateGraph` | `WorkflowGraph` | 更简单，只支持线性路径 + 条件边 |
| `AgentState` (TypedDict) | `WorkflowState` (Java class) | 用 ConcurrentHashMap/CopyOnWriteArrayList 保证线程安全 |
| `Node` (Python function) | `WorkflowNode` (Java interface) | 返回 `Flux<WorkflowEvent>` 支持流式 |
| `conditional_edge` | `WorkflowEdge` with `Predicate` | 支持条件转移 |
| `graph.invoke()` | `WorkflowEngine.execute()` | 用 Reactor 操作符组合 |

**关键决策**：不构建通用 DAG 引擎，而是针对 4 层流水线的固定结构，用 Reactor 的 `Flux.concatMap`（串行）和 `Flux.merge`（并行）直接组合。这比通用图引擎更简单、更高效。

### 2.2 响应式流驱动

整个工作流产出一个 `Flux<WorkflowEvent>`，从第一个分析师开始到最后一个裁决官结束。这个 Flux 是**单播**的——一个 SSE 连接承载所有 4 层的输出。

```
Flux<WorkflowEvent> = Flux.concat(
    layer1.execute(),   // 并行（内部用 Flux.merge）
    layer2.execute(),   // 辩论（内部用 Flux.concat 逐轮）
    layer3.execute(),   // 交易
    layer4.execute()    // 风险评估
)
```

### 2.3 组合优于继承

节点之间通过**组合**而非继承建立关系：
- `ParallelFanOutNode` 持有 `List<WorkflowNode>`，用 `Flux.merge` 并行执行
- `BullBearDebateOrchestrator` 持有 `DebateNode` × 2 + `JudgeNode`，编排辩论流程
- `WorkflowAgentFactory` 负责组装，节点本身不依赖工厂

---

## 3. 四层流水线详解

### 3.1 Layer 1：并行数据采集

**目标**：3 个专业分析师同时工作，各自用专属工具采集数据。

```
                    ┌── MarketAnalyst ──┐
                    │   (FinancialData) │
User Query ────────┼── Fundamentals ───┼──▶ analystReports
                    │   (FinancialData  │    (ConcurrentHashMap)
                    │    + Sql + Calc)  │
                    └── NewsAnalyst ────┘
                        (WebFetch + MCP)
```

**并行机制**：`ParallelFanOutNode` 用 `Flux.merge(concurrency, streams)` 合并 3 个分析师的流：

```java
// ParallelFanOutNode.execute()
List<Flux<WorkflowEvent>> streams = parallelNodes.stream()
    .map(node -> node.execute(state, sink))
    .toList();

return Flux.merge(parallelNodes.size(), streams.toArray(new Flux[0]));
```

- `Flux.merge` 不保证顺序，谁先产出事件谁先推送
- `concurrency = 3` 确保 3 个流同时活跃
- 每个分析师内部有独立的 `ChatClient`，工具调用互不干扰

**工具隔离**：每个分析师只看到自己需要的工具：

| 分析师 | 绑定工具 |
|--------|----------|
| MarketAnalyst | a_stock_quote, a_stock_signal, a_stock_limit_up, market_data |
| FundamentalsAnalyst | a_stock_quote, a_stock_report, a_stock_news, a_stock_capital, market_data, financial_calculator, executeQuery |
| NewsAnalyst | a_stock_news, a_stock_report, a_stock_signal, bailian_web_search |

### 3.2 Layer 2：多空辩论

**目标**：看多和看空研究员交替辩论，研究主管裁决。

```
Round 1:  Bull ──▶ Bear
Round 2:  Bull ──▶ Bear
          │
          ▼
    ResearchManager (裁决) ──▶ investmentPlan
```

**辩论编排**：`BullBearDebateOrchestrator` 用 `Flux.concat` + `Flux.defer` 实现严格交替：

```java
Flux<WorkflowEvent>[] roundFluxes = new Flux[rounds * 2];
for (int i = 0; i < rounds; i++) {
    roundFluxes[i * 2]     = Flux.defer(() -> bull.debateRound(state, history, sink));
    roundFluxes[i * 2 + 1] = Flux.defer(() -> bear.debateRound(state, history, sink));
}
return Flux.concat(roundFluxes);
```

**为什么用 `Flux.defer`？**
- `debateRound()` 内部会读取 `debateHistory` 来确定当前轮次
- 如果不用 `defer`，所有轮次在构造时就捕获了同一个空 history 的快照
- `defer` 确保每轮在实际执行时才读取最新的 history

**辩论上下文传递**：每轮辩论的 prompt 包含：
1. 所有分析师报告（来自 Layer 1 的 `state.analystReports`）
2. 之前的辩论记录（`debateHistory` 列表）
3. 当前轮次编号

### 3.3 Layer 3：交易决策

**目标**：交易员基于投资计划 + 分析报告，制定具体交易方案。

```java
// TraderNode.execute()
ChatClient client = ChatClient.builder(chatModel)
    .defaultToolCallbacks(traderTools)  // calculate, peRatio, pbRatio
    .defaultSystem(systemPrompt)
    .build();

return client.prompt()
    .user(buildTraderPrompt(state))  // 包含 investmentPlan + analystReports
    .options(options)                // 含 toolContext（守卫追踪器）
    .stream().content();
```

交易员可以调用计算工具（`calculate`, `peRatio`, `pbRatio`）来计算估值指标。

### 3.4 Layer 4：风险评估（三方辩论）

**目标**：激进、保守、中立三个视角评估交易方案，风险裁决官做最终裁决。

```
Round 1:  Aggressive ──▶ Conservative ──▶ Neutral
Round 2:  Aggressive ──▶ Conservative ──▶ Neutral
          │
          ▼
    RiskJudge (裁决) ──▶ FinalDecision (JSON)
```

**结构化输出**：RiskJudge 的 system prompt 要求输出严格 JSON：

```json
{
  "action": "BUY",
  "confidence": 0.75,
  "target_price": 120.50,
  "summary": "综合评估：美光科技当前估值合理，技术面显示反弹信号..."
}
```

**JSON 解析**：`RiskDebateOrchestrator.parseDecision()` 用正则提取 JSON 字段，失败时 fallback 到文本推断：

```java
private FinalDecision parseDecision(String content) {
    Matcher matcher = JSON_PATTERN.matcher(content);
    if (matcher.find()) {
        String json = matcher.group();
        String action = extractJsonValue(json, "action");
        double confidence = Double.parseDouble(extractJsonValue(json, "confidence"));
        // ...
    }
    // fallback: 从文本中推断 BUY/SELL/HOLD
}
```

---

## 4. 工作流引擎设计

### 4.1 WorkflowNode 接口

```java
public interface WorkflowNode {
    String name();                                           // 节点名称
    Flux<WorkflowEvent> execute(WorkflowState state,        // 共享状态
                                 Sinks.Many<WorkflowEvent> sink);  // 事件推送器
}
```

**设计要点**：
- 返回 `Flux<WorkflowEvent>` —— 支持流式输出，每个 chunk 都是可推送的事件
- 接收 `Sinks.Many` —— 节点可以通过 sink 推送旁路事件（如 phaseStart/phaseComplete）
- 接收 `WorkflowState` —— 所有节点读写同一个共享状态

### 4.2 WorkflowGraph 有向图

```java
public class WorkflowGraph {
    private final Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
    private final List<WorkflowEdge> edges = new ArrayList<>();
    private String startNode;

    // 链式 API
    graph.addNode(layer1)
         .addNode(layer2)
         .addNode(layer3)
         .addNode(layer4)
         .addEdge("Layer1", "Layer2")
         .addEdge("Layer2", "Layer3")
         .addEdge("Layer3", "Layer4")
         .setStart("Layer1");
}
```

**路径解析**：`resolveExecutionPath(state)` 从 startNode 出发，沿边遍历，返回拓扑排序的节点列表。支持条件边（`Predicate<WorkflowState>`），可根据状态决定走哪条路径。

### 4.3 WorkflowEngine 执行引擎

```java
public Flux<WorkflowEvent> execute(WorkflowGraph graph, WorkflowState state) {
    Sinks.Many<WorkflowEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    return Flux.fromIterable(graph.resolveExecutionPath(state))
        .concatMap(node -> {                    // 串行执行各层
            state.setCurrentPhase(node.name());
            sink.tryEmitNext(WorkflowEvent.phaseStart(node.name()));
            return node.execute(state, sink);
        })
        .mergeWith(sink.asFlux())               // 合并旁路事件
        .doOnComplete(() -> sink.tryEmitComplete())
        .onErrorResume(e -> {
            sink.tryEmitNext(WorkflowEvent.error(e.getMessage()));
            sink.tryEmitComplete();
            return Flux.empty();
        });
}
```

**关键操作符**：
- `Flux.concatMap` —— 等前一个节点完成后再执行下一个，保证层间串行
- `mergeWith(sink.asFlux())` —— 合并节点通过 sink 推送的旁路事件（如 phaseStart）
- `onErrorResume` —— 错误恢复，推送 ERROR 事件而不是直接断开

---

## 5. Agent 节点实现

### 5.1 AnalystNode（分析师节点）

这是最复杂的节点，因为它需要：
1. 独立的 `ChatClient`（工具隔离）
2. 工具调用循环（Spring AI 自动驱动）
3. 守卫系统集成（防止工具调用失控）
4. 流式输出

```java
public class AnalystNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final ToolGuardProperties guardProperties;

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        // 1. 构建独立的 ChatClient（关键：每个分析师有自己的工具集）
        ChatClient agentClient = ChatClient.builder(chatModel)
            .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
            .defaultSystem(systemPrompt)
            .build();

        // 2. 构建 prompt
        String userPrompt = "请对以下标的进行深度分析：\n\n" + state.getOriginalQuery();

        // 3. 配置选项（含守卫追踪器）
        AnthropicChatOptions options = AnthropicChatOptions.builder()
            .thinking(AnthropicApi.ThinkingType.DISABLED, null)
            .temperature(0.4)
            .maxTokens(8192)
            .toolContext(Map.of(                          // 守卫系统集成
                MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0),
                MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(3, 0.8),
                MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(3),
                MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, new FetchSessionTracker(3, 2),
                MaxToolCallManager.SEARCH_SESSION_TRACKER_KEY, new SearchSessionTracker(1)
            ))
            .build();

        // 4. 流式调用，逐 chunk 推送事件
        StringBuilder report = new StringBuilder();
        return agentClient.prompt()
            .user(userPrompt)
            .options(options)
            .stream().content()
            .map(chunk -> {
                report.append(chunk);
                return WorkflowEvent.agentChunk(roleName, chunk);
            })
            .doOnComplete(() -> {
                state.getAnalystReports().put(roleName,
                    new AgentReport(roleName, report.toString(), Instant.now()));
            });
    }
}
```

**工具调用循环**：Spring AI 框架在检测到模型返回 `tool_use` 时，自动调用 `MaxToolCallManager.executeToolCalls()`，执行工具，将结果喂回模型，循环直到模型不再请求工具。这个循环对 `AnalystNode` 是透明的。

### 5.2 DebateNode（辩论节点）

辩论节点不使用工具，核心是构建包含完整上下文的 prompt：

```java
public Flux<WorkflowEvent> debateRound(WorkflowState state,
                                         List<DebateMessage> debateHistory,
                                         Sinks.Many<WorkflowEvent> sink) {
    String prompt = buildDebatePrompt(state, debateHistory, round);
    // prompt 包含：分析师报告 + 历史辩论记录 + 当前轮次
}

private String buildDebatePrompt(WorkflowState state, List<DebateMessage> history, int round) {
    StringBuilder sb = new StringBuilder();
    // 汇总所有分析师报告
    state.getAnalystReports().forEach((name, report) ->
        sb.append("### ").append(name).append("\n").append(report.reportContent()).append("\n\n"));
    // 加入历史辩论
    for (DebateMessage msg : history) {
        sb.append("**").append(msg.speakerName()).append("**:\n")
          .append(msg.argument()).append("\n\n");
    }
    sb.append("## 当前轮次：第").append(round).append("轮\n");
    return sb.toString();
}
```

### 5.3 ParallelFanOutNode（并行扇出节点）

```java
public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
    List<Flux<WorkflowEvent>> streams = parallelNodes.stream()
        .map(node -> node.execute(state, sink))
        .toList();

    return Flux.merge(parallelNodes.size(), streams.toArray(new Flux[0]))
        .doOnComplete(() -> sink.tryEmitNext(WorkflowEvent.phaseComplete(name)));
}
```

`Flux.merge(concurrency, ...)` 的 `concurrency` 参数确保所有流同时活跃。

---

## 6. 状态管理与线程安全

### 6.1 WorkflowState 设计

```java
@Data
public class WorkflowState {
    // Layer 1 输出（3 个分析师并行写入）
    private Map<String, AgentReport> analystReports = new ConcurrentHashMap<>();

    // Layer 2 输出（串行写入）
    private List<DebateMessage> bullBearDebate = new CopyOnWriteArrayList<>();

    // Layer 4 输出（串行写入）
    private List<DebateMessage> riskDebate = new CopyOnWriteArrayList<>();

    // 引擎内部
    private volatile String currentPhase;
    private transient Sinks.Many<WorkflowEvent> eventSink;
}
```

**为什么选择这些并发容器？**

| 容器 | 使用场景 | 原因 |
|------|----------|------|
| `ConcurrentHashMap` | analystReports | Layer 1 四个分析师并行写入，需要线程安全的 Map |
| `CopyOnWriteArrayList` | bullBearDebate, riskDebate | 读多写少（辩论历史被反复读取构建 prompt），写时复制开销可接受 |
| `volatile` | currentPhase | 单写多读，volatile 保证可见性 |
| `transient` | eventSink | 不需要序列化，运行时注入 |

### 6.2 数据流传递

```
Layer 1 (并行):  3 个 AnalystNode 各自写入 state.analystReports.put(name, report)
                      │
                      ▼
Layer 2 (串行):  DebateNode 读取 state.analystReports 构建 prompt
                 BullBearDebateOrchestrator 写入 state.bullBearDebate
                 JudgeNode 写入 state.investmentPlan
                      │
                      ▼
Layer 3 (串行):  TraderNode 读取 state.investmentPlan + state.analystReports
                 写入 state.tradingProposal
                      │
                      ▼
Layer 4 (串行):  RiskDebateOrchestrator 读取全部状态
                 写入 state.riskDebate + state.finalDecision
```

---

## 7. SSE 流式输出协议

### 7.1 事件类型

```java
public enum WorkflowEventType {
    AGENT_START,      // 分析师/裁决者开始工作
    AGENT_CHUNK,      // 流式输出的一段文本
    AGENT_COMPLETE,   // 分析师/裁决者完成
    DEBATE_START,     // 辩论开始（含轮次）
    DEBATE_CHUNK,     // 辩论流式输出
    DEBATE_COMPLETE,  // 一轮辩论完成
    PHASE_START,      // 阶段开始
    PHASE_COMPLETE,   // 阶段完成
    FINAL_DECISION,   // 最终决策
    ERROR             // 错误
}
```

### 7.2 SSE 线路格式

```
event: workflow
data: {"type":"PHASE_START","agentName":null,"content":null,"phase":"Layer1_DataCollection","timestamp":"..."}

event: workflow
data: {"type":"AGENT_START","agentName":"MarketAnalyst","content":null,"phase":null,"timestamp":"..."}

event: workflow
data: {"type":"AGENT_CHUNK","agentName":"MarketAnalyst","content":"## 技术分析\n当前价: 1132.33...","phase":null,"timestamp":"..."}

event: workflow
data: {"type":"AGENT_COMPLETE","agentName":"MarketAnalyst","content":"<完整报告>","phase":null,"timestamp":"..."}

event: workflow
data: {"type":"DEBATE_START","agentName":"BullResearcher","content":"round:1","phase":null,"timestamp":"..."}

event: workflow
data: {"type":"FINAL_DECISION","agentName":"RiskJudge","content":"综合评估...","phase":"layer4","timestamp":"..."}

event: done
data: {"conversationId":123}
```

### 7.3 前端事件处理

```typescript
// frontend/src/api/chat.ts
export function streamDeepAnalysis(
  conversationId: number,
  message: string,
  token: string,
  callbacks: WorkflowCallbacks,
): AbortController {
  // SSE 连接
  fetch(`/agent/chat/deep-analysis?conversationId=${conversationId}&message=${encodeURIComponent(message)}`, {
    headers: { Authorization: `Bearer ${token}` },
    signal: controller.signal,
  }).then(async (res) => {
    const reader = res.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      // 解析 SSE 事件，分发到回调
      for (const event of parsedEvents) {
        if (event.event === 'workflow') {
          callbacks.onEvent(JSON.parse(event.data));
        } else if (event.event === 'done') {
          callbacks.onDone();
        }
      }
    }
  });
}
```

---

## 8. 工具隔离与复用

### 8.1 工具注册模式

`WorkflowAgentFactory` 复用 `AgentLoopImpl` 相同的工具注册模式：

```java
// 构造时注册所有工具（与 AgentLoopImpl 相同）
ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
    .toolObjects(fileReadTool, fileWriteTool, fileListTool,
                 financialCalcTool, financialDataTool, sqlTool, webFetchTool)
    .build();

List<ToolCallback> callbacks = new ArrayList<>();
for (ToolCallback cb : provider.getToolCallbacks()) {
    callbacks.add(new ToolEnabledCheckWrapper(cb, toolConfigService));  // 运行时开关
}

// MCP 工具也注册
if (mcpProvider != null) {
    for (ToolCallback mcp : mcpProvider.getToolCallbacks()) {
        callbacks.add(new ToolEnabledCheckWrapper(mcp, toolConfigService));
    }
}
```

### 8.2 工具隔离

每个分析师通过 `ChatClient.builder(chatModel).defaultToolCallbacks(scopedTools)` 创建独立的 `ChatClient`，只绑定自己需要的工具：

```java
public AnalystNode createAnalyst(AgentRole role) {
    Set<String> allowedTools = Set.copyOf(role.toolNames());
    List<ToolCallback> scoped = Arrays.stream(allToolCallbacks)
        .filter(cb -> allowedTools.contains(cb.getToolDefinition().name()))
        .collect(Collectors.toList());
    return new AnalystNode(chatModel, role.roleName(), role.systemPrompt(), scoped, guardProperties);
}
```

**效果**：
- MarketAnalyst 只能看到行情工具，不会调用 SQL 查询
- NewsAnalyst 只能看到搜索和抓取工具，不会调用金融计算器
- 每个分析师的工具调用互不干扰，守卫追踪器也是独立的

---

## 9. 守卫系统集成

### 9.1 为什么需要守卫？

分析师节点使用工具时，可能陷入无限循环（反复调用相同工具、信息增益为零等）。现有的 `MaxToolCallManager` 已经实现了完善的守卫系统，需要在工作流节点中复用。

### 9.2 toolContext 注入

```java
AnthropicChatOptions options = AnthropicChatOptions.builder()
    .toolContext(Map.of(
        MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0),           // 调用计数
        MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(3, 0.8),    // 信息增益检测
        MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(3),    // 重复调用检测
        MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, new FetchSessionTracker(3, 2), // 抓取限制
        MaxToolCallManager.SEARCH_SESSION_TRACKER_KEY, new SearchSessionTracker(1)   // 搜索限制
    ))
    .build();
```

`toolContext` 通过 Spring AI 的 `AnthropicChatOptions` 传递给框架，`MaxToolCallManager` 在每次工具调用时从 context 中提取这些追踪器。

### 9.3 守卫效果

从实际运行日志可以看到守卫系统的工作：

```
[MaxToolCallManager] 工具调用轮次: 1/30, 工具: [getUSStockQuote]    // 正常调用
[MaxToolCallManager] 搜索次数超限(2/1), 拒绝搜索                      // 搜索限制生效
[MaxToolCallManager] 检测到重复URL，跳过抓取                           // URL 去重生效
[MaxToolCallManager] 达到 fetch 硬上限 (fetchCount=3), 强制停止       // 抓取限制生效
```

---

## 10. 持久化与配置

### 10.1 WorkflowAnalysis 实体

```java
@Entity
@Table(name = "workflow_analyses")
public class WorkflowAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long conversationId;
    private String originalQuery;
    private String analystReportsJson;     // JSON: {agentName: reportContent}
    private String bullBearDebateJson;     // JSON: [{speakerName, argument, timestamp}]
    private String investmentPlan;
    private String tradingProposal;
    private String riskDebateJson;
    private String action;                 // BUY/SELL/HOLD
    private Double confidence;             // 0.0 ~ 1.0
    private Double targetPrice;
    private String summary;
    private LocalDateTime createdAt;
}
```

### 10.2 配置项

```yaml
workflow:
  deep-analysis:
    enabled: true              # 总开关
    bull-bear-rounds: 2        # 多空辩论轮次
    risk-rounds: 2             # 风险评估辩论轮次
    analyst-max-tokens: 8192   # 分析师 maxTokens
    analyst-temperature: 0.4   # 分析师 temperature
    debate-max-tokens: 4096    # 辩论者 maxTokens
    debate-temperature: 0.5    # 辩论者 temperature
    timeout-seconds: 600       # 总超时
```

---

## 11. 前端架构

### 11.1 触发机制

用户输入包含"深度分析"、"全面分析"、"深度研究"等关键词时，自动切换到工作流模式：

```typescript
function isDeepAnalysisRequest(text: string): boolean {
  const keywords = ['深度分析', '全面分析', '深度研究', '深度调研', '多维度分析'];
  return keywords.some((kw) => text.includes(kw));
}
```

### 11.2 WorkflowPanel 组件

```
┌─────────────────────────────────────────────┐
│ 深度分析工作流                          ✓ 完成 │
├─────────────────────────────────────────────┤
│ ○ 数据采集  ○ 多空辩论  ○ 交易决策  ○ 风险评估 │
├─────────────────────────────────────────────┤
│ ▼ 数据采集                                    │
│ ┌─────────────────────────────────────────┐ │
│ │ 技术分析师                       ✓      │ │
│ │ ┌─────────────────────────────────────┐ │ │
│ │ │ ## 技术分析                         │ │ │
│ │ │ 当前价: 1132.33, 涨跌幅: -6.69%    │ │ │
│ │ │ ...                                 │ │ │
│ │ └─────────────────────────────────────┘ │ │
│ ├─────────────────────────────────────────┤ │
│ │ 基本面分析师                     ✓      │ │
│ │ ...                                     │ │
│ └─────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ ▶ 多空辩论                                    │
├─────────────────────────────────────────────┤
│ ▶ 交易决策                                    │
├─────────────────────────────────────────────┤
│ ▶ 风险评估                                    │
├─────────────────────────────────────────────┤
│ ┌─ 最终裁决 ─────────────────────────────┐  │
│ │ action: BUY, confidence: 0.75          │  │
│ │ target_price: 120.50                   │  │
│ │ summary: 综合评估...                    │  │
│ └────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

组件通过 `watch(events)` 响应式更新，每个新事件到达时自动更新对应 Agent 的状态。

---

## 12. 技术亮点总结（简历素材）

### 12.1 一句话描述

> 基于 Spring Boot + Spring AI 构建多智能体深度分析系统，实现 4 层流水线（并行数据采集 → 多空辩论 → 交易决策 → 风险评估），11 个 AI Agent 协同工作，支持 SSE 流式输出全流程结果。

### 12.2 技术亮点

**1. 响应式多 Agent 编排引擎**
- 设计轻量级工作流引擎（WorkflowNode/Graph/Engine），用 Reactor 的 `Flux.concatMap`（串行）和 `Flux.merge`（并行）实现层间串行、层内并行的执行模型
- 比通用 DAG 引擎更简洁高效，专为固定流水线优化

**2. 工具隔离与复用**
- 每个 Agent 通过独立的 `ChatClient` 绑定专属工具集，实现工具隔离
- 复用现有 7 个工具 Bean（金融计算、行情查询、SQL、网页抓取等），零重复注册
- 运行时工具开关通过 Redis + 装饰器模式实现

**3. 辩论驱动的决策质量提升**
- 创新的多角色辩论机制：Bull vs Bear（2 轮）+ 激进/保守/中立（2 轮）
- 对立角色交替辩论，裁决者综合裁决，比单次推理更可靠
- `Flux.defer` + `CopyOnWriteArrayList` 保证辩论历史在轮间正确传递

**4. 工具调用守卫系统**
- 集成 5 维守卫追踪器：调用计数、信息增益检测、重复调用检测、抓取限制、搜索限制
- 软限制注入 GUARD_SIGNAL 引导模型停止，硬限制强制中断
- 防止 Agent 陷入工具调用死循环

**5. 全链路 SSE 流式输出**
- 单个 SSE 连接承载 4 层所有 Agent 的实时输出
- 前端渐进式渲染：分析师报告实时更新、辩论过程逐句展示、最终决策卡片
- 10 种事件类型覆盖完整生命周期

**6. 结构化输出与容错**
- RiskJudge 输出 JSON 格式的最终决策（action/confidence/target_price/summary）
- 正则提取 + 字段校验 + 文本推断 fallback 的多层容错

### 12.3 技术栈关键词

`Spring Boot 3.5` `Spring AI 1.0` `Reactor (WebFlux)` `SSE` `多智能体协作` `工具调用循环` `辩论决策机制` `响应式编程` `ConcurrentHashMap` `CopyOnWriteArrayList` `JPA` `Redis` `Vue 3`

### 12.4 可量化的成果

- **11 个 AI Agent** 角色，各自有专属 system prompt 和工具集
- **4 层流水线**，Layer 1 三路并行，Layer 2/4 各 2 轮辩论
- **单次分析约 16 次 LLM 调用**（3 分析师 + 4 辩论 + 1 裁决 + 1 交易 + 6 风险辩论 + 1 最终裁决）
- **10 种 SSE 事件类型**，覆盖全流程生命周期
- **全链路流式输出**，用户可实时看到每个 Agent 的工作进展

---

## 13. 与 LangGraph 的对比

| 维度 | LangGraph (Python) | 本项目 (Java) |
|------|-------------------|---------------|
| 图定义 | `StateGraph` + `add_node` + `add_edge` | `WorkflowGraph` + `addNode` + `addEdge` |
| 状态 | `TypedDict` (Python dict) | `WorkflowState` (Java class + 并发容器) |
| 执行 | `graph.invoke()` / `graph.stream()` | `WorkflowEngine.execute()` 返回 `Flux` |
| 并行 | `Send` API | `Flux.merge(concurrency, streams)` |
| 条件边 | `add_conditional_edges` | `addEdge(from, to, Predicate)` |
| 流式 | `graph.stream()` yields events | `Flux<WorkflowEvent>` SSE |
| 持久化 | 内置 checkpoint | 自定义 JPA 实体 |
| 工具 | `ToolNode` | `AnalystNode` 内嵌 `ChatClient` |

**本项目的创新点**：
1. 用 Reactor 操作符直接组合，比通用图引擎更轻量
2. 辩论机制比 LangGraph 的简单路由更复杂（多轮交替 + 裁决）
3. 工具隔离通过 `ChatClient` 级别实现，比 LangGraph 的全局工具注册更精细
4. 守卫系统集成，防止工具调用失控（LangGraph 无此机制）
