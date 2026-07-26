# 小墨项目功能与技术亮点

> 最后更新：2026-07-22

## 一、项目定位

小墨是一个面向金融投资场景的 AI Agent 系统，基于 Spring AI 框架构建。核心思路是：用户用自然语言描述需求 → Agent 理解意图 → 自动调用金融工具 → 返回分析结果。

**技术栈**：Java 17 + Spring Boot 3.5 + Spring AI 1.0 + Vue 3 + PostgreSQL + Redis

---

## 二、核心功能模块

### 2.1 AI Agent 对话系统

**同步/流式双模式**

- 同步接口：`POST /agent/chat`，一次性返回完整结果
- 流式接口：`GET /agent/chat/stream`，基于 SSE (Server-Sent Events) 实时输出
- 前端通过 `fetch` + `ReadableStream` 解析 SSE 流，支持三种事件类型：`status`（状态）、`content`（内容增量）、`done`（完成）

**上下文管理**

- 维护最近 50 条消息的对话窗口
- Redis 缓存会话列表（30min）、消息（1h）、最近消息（10min）
- 72 小时 Token 有效期，单设备登录

### 2.2 工具调用系统

**工具统计**：19 个工具类，44 个 `@Tool` 注解方法

#### 基础工具

| 工具 | 功能 | 方法数 |
|------|------|--------|
| FinancialCalcTool | 复利、NPV、IRR、夏普比率等金融计算 | 22 |
| FinancialDataTool | A股/港股/美股行情、基金净值查询 | 5 |
| SqlTool | 数据库查询（只读，禁止危险关键词） | 2 |
| WebFetchTool | 网页抓取（限 20K 字符，屏蔽内网 IP） | 2 |
| FileReadTool / FileWriteTool / FileListTool | 文件读写操作 | 4 |
| YangJiBaoTool | 益军宝基金平台集成 | 2 |
| GetAnalysisReportTool | 分析报告获取 | 1 |

#### A 股数据工具集（8 个 Router Tool）

基于 Router 架构设计，每个 Router 封装同一领域的多个 API 端点：

| Router | 覆盖领域 | 数据源 |
|--------|----------|--------|
| AStockQuoteRouterTool | 实时行情、K线、盘口 | 腾讯、百度 |
| AStockReportRouterTool | 研报、EPS 预测、语义搜索 | 东财、同花顺、iwencai |
| AStockSignalRouterTool | 热点板块、龙虎榜、解禁 | 东财 |
| AStockCapitalRouterTool | 融资融券、大宗交易、股东 | 东财 |
| AStockNewsRouterTool | 个股新闻、公告、互动问答 | 东财、巨潮、新浪 |
| AStockLimitUpRouterTool | 涨停池、连板梯队、跌停池 | 东财、同花顺 |
| AStockOptionRouterTool | ETF 期权 T 型报价、希腊字母 | 新浪 |
| AStockSentimentRouterTool | 热榜、人气榜、概念命中 | 同花顺、东财 |

**东财限流器**：`EastMoneyRateLimiter` 实现串行限流（≥1s 间隔 + 随机抖动），避免触发东财接口封禁。

#### 外部服务集成

- **MCP 协议**：集成百度 AI 搜索服务，60 秒保活机制
- **iwencai**：自然语言股票搜索（可选配置）

### 2.3 深度分析工作流

基于 Reactor 响应式编程构建的多智能体协作引擎，包含 5 个阶段：

```
Layer1: 三路并行数据采集（市场分析师 / 基本面分析师 / 新闻分析师）
    ↓
Layer2: 多空辩论（多轮辩论，可配置轮数）
    ↓
Layer3: 交易员提案
    ↓
Layer4: 风险辩论
    ↓
Layer5: 风险裁决官覆盖
```

**技术细节**：

- `WorkflowGraph`：DAG 图结构，支持条件边
- `WorkflowNode`：节点接口，可扩展
- 支持取消机制（cancelSink + Disposable）
- 时间预算控制（默认 600 秒超时）
- 免费额度扣减
- 分析结果持久化到数据库

**前端可视化**：`WorkflowPanel` 组件实时展示各 Agent 状态和输出，分 4 个阶段面板呈现。

### 2.4 用户记忆系统

两层记忆架构：

**用户画像（UserProfile）**

- 6 个类别：投资风格、风险偏好、关注行业、持仓偏好、交互习惯、其他
- 支持用户主动触发："记住我偏好价值投资"
- 异步 AI 提取：每 5 轮对话自动触发画像更新
- 每用户上限 50 条画像记录

**对话摘要（ConversationSummary）**

- 消息超过 20 条时触发 AI 摘要压缩
- 10:1 压缩比，保留关键信息
- 注入 System Prompt 保持上下文连贯

**记忆开关**：用户可在设置中关闭记忆功能（总开关 + 对话摘要压缩开关）。

### 2.5 工具调用防护（Tool Guard）

6 个协作组件防止 Agent 陷入无限调用循环：

| 组件 | 功能 |
|------|------|
| GuardSignal | 信号聚合，输出 5 级信号（NONE/ADVISORY/WARNING/CRITICAL/FORCE） |
| RepetitionDetector | 滑窗检测重复工具调用 |
| InfoGainTracker | 衡量工具返回结果的信息增益 |
| SearchSessionTracker | 控制搜索轮次上限 |
| FetchSessionTracker | URL 去重和连续无新信息检测 |
| ReportCompletenessChecker | 检查报告字数和章节数 |

### 2.6 意图分类器

`RuleBasedIntentClassifier` 实现 4 级优先匹配：

1. 高特异性意图（持仓查询、深度分析）
2. 金融专业意图（新闻、板块、计算、数据库）
3. 个股分析（需要标的解析，成本最高）
4. 兜底通用对话

`IntentToolGroupMap` 将 9 种意图映射到工具白名单，实现意图级工具过滤，减少无关工具加载。

### 2.7 养基宝集成

- QR 码登录获取 Token
- 账户列表、持仓明细、基金估值查询
- OkHttp + MD5 签名
- 持仓数据事务写入 + Redis 缓存
- 前端组件：账户汇总卡片、持仓展示、指数行情

### 2.8 用户配置系统

- 支持用户自定义 API Key 和模型配置
- 用户级 ChatModel：有自定义配置时使用用户级，否则回退全局默认
- 工具开关：可单独启用/禁用每个工具
- API 渠道管理：支持多渠道配置

---

## 三、前端功能

### 3.1 页面结构

| 页面 | 功能 |
|------|------|
| PortalView | 产品官网着陆页（特性展示、FAQ、CTA） |
| Login | 登录页，含多种动画背景（K线、粒子、渐变、波浪） |
| ChatView | 主聊天界面 |

### 3.2 Markdown 渲染

自研 `MarkdownRenderer`，支持：
- 代码块（语法高亮）
- 表格
- 数学公式
- 引用块
- 列表
- 标题
- 分割线

使用 `marked` + `highlight.js` + `DOMPurify` 实现安全渲染。

### 3.3 流式对话体验

- `streamChat`：通用 SSE 流式对话
- `streamDeepAnalysis`：深度分析工作流专用 SSE 流
- 消息逐字追加显示
- 流式光标动画

### 3.4 分析系统

- 分析记录列表、详情查看
- 分析启动/取消
- SSE 回放
- 导出支持：PDF、Word、Markdown

### 3.5 状态管理（Pinia）

| Store | 职责 |
|-------|------|
| chat | 会话列表、消息 CRUD、流式追加 |
| analysis | 分析记录管理 |
| auth | 认证状态 |
| yangjibao | 基金持仓同步、指数行情 |
| notification | 通知管理 |

---

## 四、工程设计特点

### 4.1 Router 工具架构

将 40+ 个 API 按领域封装为 8 个 Router Tool，每个 Router 内部通过 `operation` 参数路由到具体操作。优势：
- 降低 Agent 工具选择复杂度
- 减少工具定义数量
- 领域内聚，便于维护

### 4.2 意图级工具过滤

根据用户意图预过滤工具列表，只加载相关工具，减少无关工具对 Agent 决策的干扰。

### 4.3 响应式工作流引擎

基于 Reactor 构建 DAG 工作流，支持：
- 并行节点执行
- 条件边路由
- 取消机制
- 超时控制

### 4.4 多级缓存策略

- Redis 会话缓存（30min）
- Redis 消息缓存（1h）
- Redis 最近消息缓存（10min）
- 用户画像缓存
- 持仓数据缓存

### 4.5 安全设计

- 密码传输：客户端 SHA-256 → 服务端 BCrypt
- SQL 工具：只读，禁止危险关键词
- 文件操作：限制项目目录
- 网页抓取：屏蔽内网 IP，限制字符数
- Token：72 小时有效期，单设备登录

### 4.6 Docker 部署

多阶段构建：Node 20（前端构建）→ JDK 17（后端编译）→ JRE 17（运行时）

---

## 五、当前状态与局限

### 已完成

- [x] AI Agent 基础框架（Spring AI + Tool Calling）
- [x] A 股数据工具集（8 个 Router，覆盖行情/研报/资金/新闻等）
- [x] SSE 流式对话
- [x] 用户记忆系统
- [x] 深度分析工作流（多智能体协作）
- [x] 工具调用防护
- [x] 意图分类与工具过滤
- [x] 益军宝基金集成
- [x] 用户配置系统

### 进行中 / 待完善

- [ ] Agent 任务规划优化（ReAct 模式）
- [ ] RAG 投研知识库
- [ ] 投资组合管理
- [ ] 回测系统

### 已知限制

- 东财接口有封禁风险，已做限流但不保证长期可用
- 记忆系统依赖 AI 提取，存在提取质量波动
- 工作流超时默认 600 秒，复杂分析可能需要更长时间

---

## 六、数据规模

| 维度 | 数量 |
|------|------|
| 工具类 | 19 个 |
| @Tool 方法 | 44 个 |
| 数据源 | 7+ 个（腾讯、东财、同花顺、巨潮、新浪、百度、iwencai） |
| Router Tool | 8 个 |
| 金融计算公式 | 22 种 |
| 意图类型 | 9 种 |
| 记忆画像类别 | 6 类 |
| 工作流阶段 | 5 层 |