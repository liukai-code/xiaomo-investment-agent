<div align="center">
  <img src="docs/pic/logo1.png" alt="小墨 Logo" width="120">
  <h1>小墨 XMO</h1>
  <p><strong>基于 Spring AI 的金融投资 AI Agent</strong></p>
  <p>自然语言理解 → 任务自动规划 → 工具调用执行 → 专业报告生成</p>
</div>

<div align="center">
  <img src="https://skillicons.dev/icons?i=java,spring,vue,ts,postgres,redis,docker" />
</div>

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0-6DB33F">
  <img src="https://img.shields.io/badge/Claude-Agent-blueviolet">
  <img src="https://img.shields.io/badge/MCP-Protocol-9b59b6">
  <img src="https://img.shields.io/badge/SSE-Streaming-e67e22">
  <img src="https://img.shields.io/badge/Java-17-orange">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green">
</div>

---

<div align="center">
  <img src="docs/pic/img_1.png" alt="小墨对话界面" width="85%">
</div>

## 项目介绍

小墨是一个**领域型 AI Agent 系统**，专为金融投资场景设计。

核心能力：用户输入自然语言 → Agent 理解意图 → 自动规划任务 → 调用专业工具 → 生成投资分析报告。

不同于简单的聊天机器人，小墨具备：
- **任务规划**：自动拆解复杂分析任务为多个步骤
- **工具调用**：根据任务类型选择合适的金融数据工具
- **多源整合**：整合 13 个数据源、40 个端点的金融数据
- **流式响应**：SSE 实时输出分析过程和结果

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户界面 (Vue 3)                       │
│                    自然语言输入 / 流式响应展示                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ SSE
┌───────────────────────────▼─────────────────────────────────┐
│                      AI Agent 核心层                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ 意图识别     │  │ 任务规划     │  │ 上下文管理   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└───────────────────────────┬─────────────────────────────────┘
                            │ Tool Call
┌───────────────────────────▼─────────────────────────────────┐
│                    Tool Router 路由层                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ 行情工具  │ │ 研报工具  │ │ 计算工具  │ │ 搜索工具  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└───────────────────────────┬─────────────────────────────────┘
                            │ API Call
┌───────────────────────────▼─────────────────────────────────┐
│                      数据源层                                 │
│  通达信 / 腾讯 / 东财 / 同花顺 / 巨潮 / 新浪 / iwencai        │
└─────────────────────────────────────────────────────────────┘
```

## 核心能力

### Agent 智能决策

- **自然语言理解**：识别用户真实意图（查询 vs 分析 vs 计算）
- **任务自动规划**：将复杂任务拆解为可执行步骤
- **多轮上下文**：维护对话历史，支持追问和深入分析
- **流式响应**：SSE 实时输出 Agent 执行过程

### 金融研究能力

- **行情查询**：实时报价、K 线数据、五档盘口
- **基本面分析**：财务指标、估值计算、盈利能力
- **技术面分析**：趋势判断、支撑压力位
- **市场情绪**：资金流向、北向资金、龙虎榜

### Tool 工具系统

- **Router 架构**：按领域划分 8 个 Router Tool，降低工具选择复杂度
- **金融计算**：22 种专业计算（复利、NPV、IRR、夏普比率等）
- **A 股数据**：13 个数据源、40 个端点的全栈数据能力
- **外部集成**：MCP 协议支持扩展外部 AI 服务

## Agent 工作流程

```
用户输入: "分析一下贵州茅台是否值得长期投资"

Agent 执行过程:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ 意图识别: 股票投资分析
✓ 任务规划: [行情查询, 财务分析, 新闻调研, 估值计算, 综合评估]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Step 1/5  获取实时行情
            → QuoteTool(600519)
            → 当前价: 1800.00 涨跌: +2.5%

  Step 2/5  查询财务指标
            → FinanceTool(600519)
            → ROE: 32.5% 净利润增长: 15.3%

  Step 3/5  获取近期新闻
            → NewsTool(600519)
            → 分析近期重大事项

  Step 4/5  计算估值水平
            → CalcTool(PE/PEG/DCF)
            → PE: 35.2 行业平均: 28.5

  Step 5/5  综合分析评估
            → 生成投资分析报告

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 贵州茅台投资分析报告

### 基本面
- 市盈率: 35.2 (行业平均 28.5，溢价 23.5%)
- ROE: 32.5% (优秀)
- 净利润增长: 15.3% (稳健)

### 估值分析
- PEG: 2.3 (偏高)
- PE 消化年数: 2.8 年

### 综合建议
短期估值偏高，但基本面优秀。
长期投资者可逢低布局，建议关注回调机会。
```

## 使用场景

| 场景 | 示例输入 | Agent 能力 |
|------|---------|-----------|
| 个股分析 | "分析茅台是否值得买入" | 行情 + 财务 + 估值 |
| 行业对比 | "对比宁德和比亚迪" | 多标的横向对比 |
| 研报检索 | "最近新能源研报" | iwencai 语义搜索 |
| 市场情绪 | "今天涨停多少家" | 打板池 + 连板梯队 |
| 资金流向 | "北向资金今天流入还是流出" | 实时资金数据 |
| 金融计算 | "贷款 100 万月供多少" | 22 种计算工具 |

## Engineering Highlights

### Tool Router 架构

将 40+ 个 API 按领域封装为 8 个 Router Tool：

```
AStockQuoteRouterTool    → 行情层（实时报价、K线、盘口）
AStockReportRouterTool   → 研报层（个股/行业研报、PDF下载）
AStockSignalRouterTool   → 信号层（热点、北向、龙虎榜）
AStockCapitalRouterTool  → 资金面（融资融券、大宗交易、股东）
AStockNewsRouterTool     → 新闻层（个股新闻、全球资讯）
AStockLimitUpRouterTool  → 打板层（涨停池、连板梯队）
AStockOptionRouterTool   → 期权层（ETF期权、希腊字母）
AStockSentimentRouterTool→ 舆情层（热榜、人气榜）
```

**设计优势**：Agent 只需选择 Router，Router 内部路由到具体 Operation，降低工具选择复杂度。

### Memory 记忆系统

- **用户画像记忆**：记住用户偏好和投资风格
- **对话摘要压缩**：长对话自动压缩，保持上下文连贯
- **多轮会话管理**：Redis 缓存 + 72h Token 有效期

### SSE 流式架构

- **后端**：Flux 累积全文 → 流式输出
- **前端**：ReadableStream 解析 → 实时渲染
- **体验**：用户可实时看到 Agent 执行过程

## 技术架构

### Backend

- **Java 17** + **Spring Boot 3.5**
- **Spring AI 1.0** - Agent 框架，Tool Calling，Context Management
- **Spring Data JPA** + **PostgreSQL** - 持久化
- **Spring Data Redis** - 缓存 / 会话 / 限流
- **MCP Protocol** - 外部 AI 服务集成

### Frontend

- **Vue 3** + **TypeScript**
- **Vite 6** - 构建工具
- **Pinia** - 状态管理
- **SSE Streaming** - 实时对话

### Infrastructure

- **Docker** 多阶段构建（Node 20 + JDK 17 + JRE 17）
- **Redis** 多级缓存策略
- **PostgreSQL** 数据持久化

## 项目结构

```
src/main/java/com/itlk/myclaudecode/
├── agent/              # AI Agent 核心（意图识别、任务规划、工具调用）
├── tool/               # Tool 工具系统
│   ├── Financial*      # 金融计算工具（22种）
│   └── astock/         # A股数据工具（8个Router）
├── conversation/       # 会话管理
├── auth/               # 认证授权
└── user/               # 用户管理

frontend/src/
├── api/chat.ts         # SSE 流式客户端
├── views/ChatView.vue  # 对话界面
├── stores/             # 状态管理
└── composables/        # Markdown 流式解析
```

详细结构请查看 [项目文档](docs/)。

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- PostgreSQL 14+
- Redis 6+

### 启动步骤

```bash
# 1. 克隆项目
git clone https://github.com/liukai-code/xiaomo-investment-agent.git
cd xiaomo-investment-agent

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入数据库、Redis、API Key 等配置

# 3. 启动后端
mvn clean package -DskipTests
mvn spring-boot:run    # 端口 4545

# 4. 启动前端
cd frontend
npm install
npm run dev           # 端口 5656，代理到 4545

# 5. 访问应用
open http://localhost:5656
```

## Roadmap

- [x] AI Agent 基础框架（Spring AI + Tool Calling）
- [x] A 股数据工具集（13 源 40 端点）
- [x] SSE 流式对话
- [x] 用户记忆系统
- [x] 记忆开关 / 对话偏好设置
- [ ] Agent 任务规划优化（ReAct 模式）
- [ ] RAG 投研知识库
- [ ] 投资组合管理
- [ ] 回测系统
- [ ] 多 Agent 协作

## License

[Apache License 2.0](LICENSE)

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI Agent 框架
- [a-stock-data](https://github.com/simonlin1212/a-stock-data) - A 股数据工具参考
