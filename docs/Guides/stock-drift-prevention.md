# 深度分析工作流 — 标的漂移防护机制

## 问题背景

深度分析工作流（`DeepAnalysisWorkflow`）采用 5 层 DAG 架构，Layer 1 有 4 个并行分析师 Agent。当用户输入股票名称（如"丰光精密"）而非数字代码时，原有系统存在三重故障：

1. **标的漂移** — 每个 Agent 各自猜测股票代码，不同分析师分析不同股票
2. **代码错配** — LLM hallucinate 出错误的代码-名称映射（如 920510 ≠ 丰光精密）
3. **Scope Guard 失效** — `StockCodeExtractor` 只能提取数字代码，名称输入时 `allowedStockCodes` 为空，工具调用无约束

## 防护架构

```
用户输入: "分析丰光精密"
        │
        ▼
┌─────────────────────────────────┐
│  Layer 0: 标的解析门控           │
│  StockCodeExtractor → StockResolver │
│  输出: code=430510, name=丰光精密   │
│  失败 → 返回错误事件，终止工作流      │
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│  WorkflowState 标的锁定          │
│  resolvedStockCode = "430510"   │
│  resolvedStockName = "丰光精密"  │
│  allowedStockCodes = {"430510"} │
│  （全局只读，不可修改）            │
└──────────────┬──────────────────┘
               │
     ┌─────────┼─────────┐
     ▼         ▼         ▼
  Analyst   Debate    Judge/Trader
  (所有节点 system prompt 注入锁定标的)
               │
               ▼
┌─────────────────────────────────┐
│  MaxToolCallManager Scope Guard │
│  拦截 stockCode ≠ 430510 的调用  │
└─────────────────────────────────┘
```

## 四层防护详解

### 第一层：入口解析门控

**文件**: `DeepAnalysisWorkflow.execute()`

工作流启动时，先通过 `StockCodeExtractor` 尝试提取数字代码。若无数字代码，调用 `StockResolver.resolve()` 将中文名称解析为确定的 `{code, name}`。

```
输入 "分析丰光精密"
  → StockCodeExtractor: 无数字代码
  → StockResolver.resolve("分析丰光精密")
    → 提取中文关键词 "丰光精密"
    → 调用东财 suggest API
    → 返回 {code: "430510", name: "丰光精密"}
  → state.resolvedStockCode = "430510"
  → state.allowedStockCodes = {"430510"}
```

解析失败时，直接返回 `WorkflowEvent.error()` 终止工作流，不会进入后续分析。

**文件**: `workflow/util/StockResolver.java`

`StockResolver` 是标的解析的核心，逻辑：

1. 正则提取查询中的 6 位数字代码 → 直接返回
2. 无数字代码时，提取中文名称关键词（≥2 字）
3. 调用东财 suggest API（`searchapi.eastmoney.com`）
4. 失败则 fallback 到新浪 suggest API（`suggest3.sinajs.cn`）
5. 返回第一个匹配的 A 股结果

### 第二层：状态锁定

**文件**: `workflow/state/WorkflowState.java`

```java
private String resolvedStockCode;   // 锁定的6位代码，如 "430510"
private String resolvedStockName;   // 锁定的名称，如 "丰光精密"
private Set<String> allowedStockCodes; // scope guard 用，始终包含 resolvedStockCode
```

这两个字段在工作流启动时由解析门控写入，之后**只读**。所有下游节点通过 `state.getResolvedStockCode()` 获取，不存在重新解析的可能。

### 第三层：Prompt 约束注入

**文件**: `AnalystNode.buildStockTargetLine()`（被所有节点复用）

每个节点的 system prompt 开头注入：

```
【分析标的】丰光精密（430510）
⚠️ 标的已锁定为 430510，禁止分析其他股票，禁止重新查询或猜测股票代码。
所有工具调用必须使用 stockCode="430510"。
```

注入位置：

| 节点 | 方法 |
|------|------|
| `AnalystNode` | `execute()` → system prompt + user prompt |
| `DebateNode` | `debateRound()` → system prompt |
| `JudgeNode` | `makeJudgment()` → system prompt |
| `TraderNode` | `execute()` → system prompt |

`AnalystNode` 的 user prompt 还额外追加：`所有 stockCode 参数必须使用 430510。`

### 第四层：工具调用 Scope Guard

**文件**: `agent/service/impl/MaxToolCallManager.java`

在每次工具调用前，检查参数中的股票代码是否在 `allowedStockCodes` 内：

```
LLM 调用: a_stock_quote(operation="tencentQuote", params={"stockCodes":"600519"})
  → extractStockCodesFromArgs → {"600519"}
  → {"600519"} ∉ {"430510"}
  → 拦截，返回 "股票代码 [600519] 不在分析范围内。请只查询目标股票: [430510]"
```

由于入口门控已保证 `allowedStockCodes` 始终非空，此守卫对所有输入场景均生效。

## 关键文件

| 文件 | 职责 |
|------|------|
| `workflow/util/StockResolver.java` | 标的名称→代码解析（东财+新浪） |
| `workflow/util/StockCodeExtractor.java` | 从查询中提取数字代码（原有） |
| `workflow/state/WorkflowState.java` | 共享状态，持有锁定标的字段 |
| `workflow/service/DeepAnalysisWorkflow.java` | 入口门控，注入解析逻辑 |
| `workflow/node/AnalystNode.java` | prompt 注入 + `buildStockTargetLine()` |
| `workflow/node/DebateNode.java` | 复用 `buildStockTargetLine()` |
| `workflow/node/JudgeNode.java` | 复用 `buildStockTargetLine()` |
| `workflow/node/TraderNode.java` | 复用 `buildStockTargetLine()` |
| `agent/service/impl/MaxToolCallManager.java` | 工具调用 scope guard |
| `tool/FinancialDataTool.java` | `getAShareQuote()` 委托 `StockResolver` |

## 数据流时序

```
1. 用户输入 → "分析丰光精密"
2. StockCodeExtractor.extract() → {}（无数字代码）
3. StockResolver.resolve("分析丰光精密") → {code: "430510", name: "丰光精密"}
4. state.setResolvedStockCode("430510")
5. state.setAllowedStockCodes({"430510"})
6. WorkflowEngine 启动 Layer 1（4 个 AnalystNode 并行）
   → 每个 AnalystNode 的 system prompt 包含 "标的已锁定为 430510"
   → LLM 调用工具时传 stockCode="430510"
   → MaxToolCallManager 校验通过
7. Layer 2-5 同样注入锁定标的
8. 全链路标的一致，无漂移
```
