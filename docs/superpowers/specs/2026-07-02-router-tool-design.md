# Router Tool 架构重构设计

## Context

当前 Agent 系统暴露 27 个 `@Tool` 方法给 LLM（21 个金融计算 + 5 个行情查询 + 1 个文件操作），加上 MCP 工具，总计 32+。当工具数量超过 15 且语义相近时，LLM 的工具选择准确率显著下降，容易出现：
- 选错工具后反复试探
- 相似工具之间犹豫（如 `dcaReturn` vs `compoundDca`）
- 无效调用浪费 token 和迭代次数

本设计通过引入 Router Tool 模式，将工具数量从 27 降至 9，同时保持现有 guard 基础设施不变。

## 架构设计

### 当前架构

```
LLM → 27 个 @Tool 方法（Spring AI MethodToolCallbackProvider 扫描）
    ├── FinancialCalcTool (21 methods)
    ├── FinancialDataTool (5 methods)
    ├── FileReadTool (1 method)
    ├── FileWriteTool (1 method)
    ├── FileListTool (1 method)
    ├── SqlTool (2 methods)
    └── WebFetchTool (2 methods)
```

### 目标架构

```
LLM → 9 个 @Tool 方法
    ├── FinancialCalcRouterTool (1 method) → 内部委托 FinancialCalcTool (21 methods)
    ├── FinancialDataRouterTool (1 method) → 内部委托 FinancialDataTool (5 methods)
    ├── FileReadTool (1 method)            → 不变
    ├── FileWriteTool (1 method)           → 不变
    ├── FileListTool (1 method)            → 不变
    ├── SqlTool (2 methods)                → 不变
    └── WebFetchTool (2 methods)           → 不变
```

### 参数模型

每个 Router Tool 采用 `operation` + `params` 双参数模型：

```java
@Tool(description = "金融计算器。执行各类金融和数学计算。...")
public String financial_calculator(
    @ToolParam(description = "操作类型") String operation,
    @ToolParam(description = "JSON格式参数") String params)
```

- `operation`: 枚举值，决定调用哪个子方法
- `params`: JSON 字符串，包含该 operation 需要的参数

路由分发逻辑：
```java
switch (operation) {
    case "compoundInterest" -> {
        var p = parseParams(params);
        return delegate.compoundInterest(p.getDouble("principal"), ...);
    }
    // ...
    default -> return "未知操作: " + operation + "。可用操作: ...";
}
```

原始 Tool 类（FinancialCalcTool、FinancialDataTool）**不删除**，Router 内部持有实例并委托调用。

## 工具描述

### financial_calculator

```
金融计算器。执行各类金融和数学计算。

operation 可选值与适用场景：

[利息与收益]
- compoundInterest: 复利终值。参数: principal, annualRate, years
- simpleInterest: 单利终值。参数: principal, annualRate, years
- annualizedReturn: 年化收益率换算。参数: totalReturnPercent, days
- dcaReturn: 定投收益（纯定投，无初始资金）。参数: monthlyAmount, annualRate, months
- compoundDca: 复利+定投综合（有初始资金+定投）。参数: initialCapital, periodicAmount, annualRate, years, frequency
- ruleOf72: 72法则（多久翻倍）。参数: annualRate
- cagr: 复合年增长率。参数: beginValue, endValue, years
- totalReturn: 总收益率（含分红）。参数: buyPrice, sellPrice, dividends
- inflationAdjusted: 通胀调整购买力。参数: amount, inflationRate, years

[估值指标]
- peRatio: 市盈率PE。参数: stockPrice, earningsPerShare
- pbRatio: 市净率PB。参数: stockPrice, bookValuePerShare
- dividendYield: 股息率。参数: annualDividend, stockPrice

[贷款]
- loanPayment: 等额本息月供。参数: principal, annualRate, months

[投资决策]
- npv: 净现值。参数: discountRate, cashFlows
- irr: 内部收益率。参数: cashFlows

[债券]
- bondPrice: 债券定价。参数: faceValue, couponRate, marketRate, periods
- bondYtm: 债券到期收益率。参数: faceValue, marketPrice, couponRate, periods

[退休规划]
- retirementTarget: 退休所需本金。参数: annualExpense, safeWithdrawalRate(可选)
- withdrawalPlan: 定额提取计划。参数: principal, annualWithdrawal, annualRate

[风险指标]
- sharpeRatio: 夏普比率。参数: portfolioReturn, riskFreeRate, volatility
- maxDrawdown: 最大回撤。参数: navSeries

[通用计算]
- calculate: 数学表达式计算。参数: expression

params 为 JSON 字符串，格式参考各 operation 的参数说明。
```

### market_data

```
行情数据查询。查询股票、基金的实时行情和基本信息。

operation 可选值：
- aShareQuote: 查询A股行情。参数: stockCodeOrName（代码或名称，代码为6位数字）
- hkStockQuote: 查询港股行情。参数: stockCode（5位数字如00700）
- usStockQuote: 查询美股行情。参数: stockCode（如AAPL）
- fundNav: 查询基金净值。参数: fundCode（6位数字）
- searchStock: 按名称搜索股票代码。参数: name

params 为 JSON 字符串。
```

## 需要修改的文件

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `tool/FinancialCalcRouterTool.java` | 新建 | Router，1 个 @Tool 方法，内部 switch 分发 |
| `tool/FinancialDataRouterTool.java` | 新建 | Router，1 个 @Tool 方法，内部 switch 分发 |
| `tool/config/ToolConfig.java` | 修改 | 注册 2 个 Router bean，注入原有 Tool bean |
| `agent/service/impl/AgentLoopImpl.java` | 修改 | 构造函数加入 Router bean，替换 toolObjects |
| `workflow/agent/AgentRole.java` | 修改 | 更新 toolNames 列表（如 `peRatio` → `financial_calculator`） |

## 不变的组件

| 组件 | 原因 |
|------|------|
| `FinancialCalcTool.java` | 保留所有子方法，Router 内部调用 |
| `FinancialDataTool.java` | 同上 |
| `MaxToolCallManager.java` | 基于 tool name 判断，新 tool name 自然匹配 |
| `RepetitionDetector.java` | 基于 tool name + args 做重复检测 |
| `InfoGainTracker.java` | 基于 tool result 做信息增益检测 |
| `FetchSessionTracker.java` | 基于 URL 做去重 |
| `SearchSessionTracker.java` | 基于 tool name 做搜索轮次限制 |
| `ToolBehaviorRegistry.java` | 自动扫描 @ToolBehavior 注解 |
| `ToolConfigService.java` | Redis key 自动适配新 tool name |
| `ToolEnabledCheckWrapper.java` | 通用包装逻辑，不感知具体 tool |

## AgentRole 迁移映射

| AgentRole | 当前 toolNames | 迁移后 |
|-----------|---------------|--------|
| MARKET_ANALYST | `getAShareQuote, getHKStockQuote, getUSStockQuote, getFundNav, searchStockByName, fetchWebpage, fetchArticleContent` | `market_data, fetchWebpage, fetchArticleContent` |
| FUNDAMENTALS_ANALYST | `getAShareQuote, getHKStockQuote, getUSStockQuote, getFundNav, getDatabaseSchema, executeQuery, fetchWebpage, fetchArticleContent, peRatio, pbRatio, dividendYield` | `market_data, financial_calculator, getDatabaseSchema, executeQuery, fetchWebpage, fetchArticleContent` |
| TRADER | `calculate, peRatio, pbRatio` | `financial_calculator` |

其他角色（NEWS_ANALYST, SOCIAL_ANALYST, BULL/BEAR_RESEARCHER 等）不涉及金融计算/行情工具，无需修改。

## 验证方案

1. 单元测试：为 FinancialCalcRouterTool 和 FinancialDataRouterTool 编写测试，验证每个 operation 的路由正确性
2. 集成测试：启动应用，通过聊天界面测试以下场景：
   - "10万元年化5%复利10年是多少" → 应调用 `financial_calculator(operation="compoundInterest", ...)`
   - "茅台股价" → 应调用 `market_data(operation="aShareQuote", ...)`
   - "PE怎么算" → 应调用 `financial_calculator(operation="peRatio", ...)`
3. Guard 验证：确认 MaxToolCallManager 的 guard 逻辑在新 tool name 下正常工作
4. Workflow 验证：确认 AgentRole 的工具过滤在迁移后正常工作
