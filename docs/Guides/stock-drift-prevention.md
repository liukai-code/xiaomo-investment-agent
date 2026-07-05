# 深度分析工作流 — 标的漂移防护机制

## 问题背景

深度分析工作流（`DeepAnalysisWorkflow`）采用 5 层 DAG 架构，Layer 1 有 4 个并行分析师 Agent。当用户输入股票名称（如"丰光精密"）而非数字代码时，原有系统存在三重故障：

1. **标的漂移** — 每个 Agent 各自猜测股票代码，不同分析师分析不同股票
2. **代码错配** — LLM hallucinate 出错误的代码-名称映射（如 920510 ≠ 丰光精密）
3. **Scope Guard 失效** — `StockCodeExtractor` 只能提取数字代码，名称输入时 `allowedStockCodes` 为空，工具调用无约束

## 防护架构（五层）

```
用户输入: "分析丰光精密"
        │
        ▼
┌──────────────────────────────────────┐
│  Layer 0: 标的解析门控                │
│  StockCodeExtractor → StockResolver  │
│  含名称匹配校验（pickBestMatch）       │
│  输出: code=430510, name=丰光精密     │
│  失败 → 返回错误事件，终止工作流        │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  WorkflowState 标的锁定               │
│  resolvedStockCode = "430510"        │
│  resolvedStockName = "丰光精密"       │
│  allowedStockCodes = {"430510"}      │
│  （全局只读，不可修改）                 │
└──────────────┬───────────────────────┘
               │
     ┌─────────┼─────────┐
     ▼         ▼         ▼
  Analyst   Debate    Judge/Trader
  (Prompt 注入: 标的锁定 + 推理边界约束)
               │
               ▼
┌──────────────────────────────────────┐
│  MaxToolCallManager                  │
│  ① 入参拦截: stockCode ≠ 430510      │
│  ② 返回硬过滤: 移除非目标标的的数据行   │
└──────────────────────────────────────┘
```

## 五层防护详解

### 第一层：入口解析门控

**文件**: `DeepAnalysisWorkflow.execute()`

工作流启动时，先通过 `StockCodeExtractor` 尝试提取数字代码。若无数字代码，调用 `StockResolver.resolve()` 将中文名称解析为确定的 `{code, name}`。

```
输入 "分析丰光精密"
  → StockCodeExtractor: 无数字代码
  → StockResolver.resolve("分析丰光精密")
    → 剥离前缀 "深度分析" → 提取关键词 "丰光精密"
    → 调用东财 suggest API，返回多个候选
    → pickBestMatch("丰光精密", candidates)
      → 候选1: "丰光精密" 430510 → 名称包含匹配 ✓ → 选中
  → state.resolvedStockCode = "430510"
  → state.allowedStockCodes = {"430510"}
```

解析失败时，直接返回 `WorkflowEvent.error()` 终止工作流，不会进入后续分析。

**文件**: `workflow/util/StockResolver.java`

`StockResolver` 是标的解析的核心，逻辑：

1. 正则提取查询中的 6 位数字代码 → 直接返回
2. 无数字代码时，提取中文名称关键词（≥2 字），剥离常见查询前缀（"深度分析"、"分析"等）
3. 调用东财 suggest API（`searchapi.eastmoney.com`），获取全部候选结果
4. 失败则 fallback 到新浪 suggest API（`suggest3.sinajs.cn`）
5. 通过 `pickBestMatch()` 从候选中选择最佳匹配（见下节）

#### 名称匹配校验（pickBestMatch）

suggest API 可能返回多个结果（同名公司、行业相关股票等），盲取第一个会导致错标。`pickBestMatch` 按优先级匹配：

| 优先级 | 规则 | 示例 |
|--------|------|------|
| 1 | 返回名称**包含**输入关键词 | 输入"茅台" → 匹配"贵州茅台" |
| 2 | 输入关键词**包含**返回名称 | 输入"丰光精密" → 匹配"丰光精密" |
| 3 | 兜底取第一个，记录 WARN 日志 | 无精确匹配时降级 |

### 第二层：状态锁定

**文件**: `workflow/state/WorkflowState.java`

```java
private String resolvedStockCode;   // 锁定的6位代码，如 "430510"
private String resolvedStockName;   // 锁定的名称，如 "丰光精密"
private Set<String> allowedStockCodes; // scope guard 用，始终包含 resolvedStockCode
```

这两个字段在工作流启动时由解析门控写入，之后**只读**。所有下游节点通过 `state.getResolvedStockCode()` 获取，不存在重新解析的可能。

### 第三层：Prompt 约束注入 + 推理边界约束

**文件**: `AnalystNode.buildStockTargetLine()`（被所有节点复用）

每个节点的 system prompt 开头注入标的锁定 + **推理边界约束**：

```
【分析标的】丰光精密（430510）
⚠️ 标的已锁定为 430510，你必须严格遵守以下约束：
1. 所有工具调用必须使用 stockCode="430510"，禁止使用其他代码
2. 禁止重新查询、猜测或推断股票代码
3. 禁止引用、分析、对比任何非 430510 的公司
4. 即使在行业分析、板块分析、概念分析中，也不得提及其他股票名称或代码
5. 若工具返回包含其他股票的数据，该数据已被系统过滤，你只能使用剩余数据
6. 你的分析范围被严格限制在丰光精密（430510）这一只股票内
```

**推理边界约束**是防止 LLM "语义漂移"的关键。即使 stockCode 参数被锁死，LLM 仍可能在推理过程中引用同行业其他公司进行对比分析。通过明确禁止引用非目标标的，将 LLM 的认知范围压缩到单一股票实体。

注入位置：

| 节点 | 方法 |
|------|------|
| `AnalystNode` | `execute()` → system prompt + user prompt |
| `DebateNode` | `debateRound()` → system prompt |
| `JudgeNode` | `makeJudgment()` → system prompt |
| `TraderNode` | `execute()` → system prompt |

### 第四层：工具入参 Scope Guard

**文件**: `agent/service/impl/MaxToolCallManager.java`

在每次工具调用**前**，检查参数中的股票代码是否在 `allowedStockCodes` 内：

```
LLM 调用: a_stock_quote(operation="tencentQuote", params={"stockCodes":"600519"})
  → extractStockCodesFromArgs → {"600519"}
  → {"600519"} ∉ {"430510"}
  → 拦截，返回 "股票代码 [600519] 不在分析范围内。请只查询目标股票: [430510]"
```

由于入口门控已保证 `allowedStockCodes` 始终非空，此守卫对所有输入场景均生效。

### 第五层：工具返回数据硬过滤

**文件**: `agent/service/impl/MaxToolCallManager.java`

部分工具（如 `conceptBlocks`、`industryRanking`、`thsHotList`、`dailyDragonTiger` 等）返回的数据可能包含**多个股票**的信息。LLM 读到其他股票数据后可能产生漂移。

在每次工具调用**后**，对返回文本执行**行级硬过滤**：逐行检查是否包含非目标标的的股票代码，若包含则**直接移除该行**，替换为过滤标记。

```
工具返回原文:
  "丰光精密(430510) 所属板块: 机器人概念
   板块内其他个股:
   三花智控(002050) 涨幅 +3.2%
   绿的谐波(688017) 涨幅 +2.1%"

硬过滤后:
  "丰光精密(430510) 所属板块: 机器人概念

   [已过滤 2 条非目标标的（[002050, 688017]）的数据，当前分析标的为 [430510]]"
```

与"追加警告"方案相比，硬过滤的优势是 LLM **根本看不到**其他股票的数据，从源头消除漂移可能。

#### 过滤范围

| 工具类型 | 示例 | 过滤行为 |
|----------|------|----------|
| 单标的工具 | `conceptBlocks`、`stockNews` | 返回通常仅含目标标的，极少触发过滤 |
| 多标的混合工具 | `industryRanking`、`thsHotList` | 涉及其他股票的数据行被移除 |
| 市场级工具 | `dailyDragonTiger`、`ztPool` | 大部分数据行被移除，仅保留目标标的相关行 |

## 关键文件

| 文件 | 职责 |
|------|------|
| `workflow/util/StockResolver.java` | 标的名称→代码解析，含名称匹配校验 |
| `workflow/util/StockCodeExtractor.java` | 从查询中提取数字代码（原有） |
| `workflow/state/WorkflowState.java` | 共享状态，持有锁定标的字段 |
| `workflow/service/DeepAnalysisWorkflow.java` | 入口门控，注入解析逻辑 |
| `workflow/node/AnalystNode.java` | prompt 注入 + `buildStockTargetLine()`（含推理边界约束） |
| `workflow/node/DebateNode.java` | 复用 `buildStockTargetLine()` |
| `workflow/node/JudgeNode.java` | 复用 `buildStockTargetLine()` |
| `workflow/node/TraderNode.java` | 复用 `buildStockTargetLine()` |
| `agent/service/impl/MaxToolCallManager.java` | 入参 scope guard + 返回数据硬过滤 |
| `tool/FinancialDataTool.java` | `getAShareQuote()` 委托 `StockResolver` |

## 数据流时序

```
1. 用户输入 → "分析丰光精密"
2. StockCodeExtractor.extract() → {}（无数字代码）
3. StockResolver.resolve("分析丰光精密")
   → 剥离前缀 → "丰光精密"
   → searchEastMoneyAll → 候选列表
   → pickBestMatch → {code: "430510", name: "丰光精密"}
4. state.setResolvedStockCode("430510")
5. state.setAllowedStockCodes({"430510"})
6. WorkflowEngine 启动 Layer 1（4 个 AnalystNode 并行）
   → system prompt 注入标的锁定 + 推理边界约束
   → LLM 调用工具时传 stockCode="430510"
   → MaxToolCallManager 入参校验通过
   → 工具返回数据若含其他股票 → 行级硬过滤移除
7. Layer 2-5 同样注入锁定标的 + 推理边界约束
8. 全链路标的一致，无漂移
```

## 已知边界情况

| 场景 | 防护层 | 风险 |
|------|--------|------|
| 用户输入数字代码 | 第一层：StockCodeExtractor 直接提取 | 无风险 |
| 用户输入精确名称 | 第一层：pickBestMatch 精确匹配 | 无风险 |
| 用户输入模糊名称（如"茅台"） | 第一层：pickBestMatch 取第一个包含匹配 | 低风险，同名公司极少 |
| LLM 尝试调用其他代码的工具 | 第四层：入参 scope guard 拦截 | 无风险 |
| 工具返回包含其他股票数据 | 第五层：行级硬过滤移除 | LLM 看不到其他股票数据 |
| LLM 在推理中引用其他公司对比 | 第三层：推理边界约束禁止 | 依赖 LLM 遵守 prompt，低风险 |
| LLM 调用市场级工具（如 thsHotList） | 第四层+第五层：工具可用但数据被过滤 | 仅保留目标标的数据 |
