<div align="center">
  <img src="docs/pic/logo.png" alt="小墨 Logo" width="80">
  <h1 style="margin-top: 0.5em; margin-bottom: 0.3em;">小墨 Xiaomo</h1>
  <p style="margin-top: 0; margin-bottom: 0.5em;"><strong>基于 Spring AI 的金融投资 AI 助手</strong></p>
  <p style="margin-top: 0; margin-bottom: 0.5em;">自然语言输入 → 意图识别 → Tool Calling → 数据分析生成</p>
</div>

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0-6DB33F">
  <img src="https://img.shields.io/badge/Tool%20Calling-FF6B35">
  <img src="https://img.shields.io/badge/Workflow-DAG-9b59b6">
  <img src="https://img.shields.io/badge/SSE-Streaming-e67e22">
</div>

<div align="center">
  <img src="https://img.shields.io/badge/Java-17-orange">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F">
  <img src="https://img.shields.io/badge/Vue-3-4FC08D">
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1">
  <img src="https://img.shields.io/badge/Redis-DC382D">
  <img src="https://img.shields.io/badge/Docker-2496ED">
</div>

---

<div align="center">
  <img src="docs/pic/img_1.png" alt="小墨对话界面" width="85%">
</div>

## 项目介绍

小墨是一个基于 Spring AI 构建的个人投资分析 AI 助手，通过 Tool Calling、Workflow、多源金融数据以及用户持仓数据集成，实现自然语言驱动的金融查询、资产分析与投资研究辅助。

核心能力：用户用自然语言描述需求 → AI 理解意图 → 调用金融工具 → 返回分析结果。

不同于简单的聊天机器人，小墨具备：
- **Tool Calling**：根据用户需求自动选择合适的金融工具
- **持仓分析**：集成养基宝 API，支持个人基金账户数据分析
- **会话记忆**：维护用户偏好和历史对话上下文
- **多源整合**：整合 13 个数据源、40 个端点的金融数据
- **流式响应**：SSE 实时输出模型响应和任务执行状态

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                     用户界面 (Vue 3 + SSE)                   │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                   Agent Service (Spring AI)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Intent Filter │  │ Context 管理 │  │ Tool Calling │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Tool Router 路由层                         │
│    行情 │ 研报 │ 资金 │ 新闻 │ 计算 │ 搜索 │ 公告 │ 期权     │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                      数据源层                                │
│  市场数据: 腾讯 / 东财 / 同花顺 / 巨潮 / 新浪 / iwencai      │
│  用户资产: 养基宝 API (基金持仓、账户数据)                     │
└─────────────────────────────────────────────────────────────┘
```

## 核心能力

### AI Agent 对话能力

- **用户意图识别**：识别用户真实需求，匹配合适的工具
- **Tool Calling**：根据任务类型自动调用对应工具
- **上下文管理**：维护对话历史，支持多轮追问
- **流式响应**：SSE 实时输出模型响应和任务执行状态

### 金融研究能力

- **行情查询**：实时报价、K 线数据、五档盘口
- **基本面分析**：财务指标、估值计算、盈利能力
- **技术面分析**：趋势判断、支撑压力位
- **市场情绪**：资金流向、北向资金、龙虎榜

### 用户持仓分析

通过集成养基宝 API，实现用户基金账户数据同步：

```
用户授权
    ↓
养基宝 API
    ↓
持仓数据同步
    ↓
Redis 缓存
    ↓
AI Agent 分析
    ↓
生成个人投资报告
```

核心能力：
- 扫码授权登录养基宝
- 获取用户账户列表
- 同步基金持仓明细
- 获取基金估值和收益数据
- 基于实时持仓生成个性化分析

### 深度分析工作流

针对复杂个股分析场景，实现基于 Workflow 的多阶段分析流程：

```
数据采集 (三路并行)
    ↓
多角色分析与观点交叉评估
    ↓
风险评估
    ↓
生成报告
```

基于 Reactor 响应式编程，支持 DAG 图结构、条件边、并行节点、取消机制。

## 使用场景

| 场景 | 示例输入 | Agent 能力 |
|------|---------|-----------|
| 个股分析 | "分析茅台是否值得买入" | 行情 + 财务 + 估值 |
| 持仓诊断 | "分析一下我的基金组合" | 持仓结构 + 收益分析 + 风险评估 |
| 基金分析 | "我的基金最近为什么跌" | 持仓变化 + 市场因素分析 |
| 行业对比 | "对比宁德和比亚迪" | 多标的横向对比 |
| 研报检索 | "最近新能源研报" | iwencai 语义搜索 |
| 市场情绪 | "今天涨停多少家" | 打板池 + 连板梯队 |
| 资金流向 | "北向资金今天流入还是流出" | 实时资金数据 |
| 金融计算 | "贷款 100 万月供多少" | 22 种计算工具 |

## 工程设计亮点

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

**设计优势**：Agent 只需选择 Router，Router 内部路由到具体操作接口（Operation），降低工具选择复杂度。

### 意图级工具过滤

通过规则识别用户任务类型，仅向 Agent 暴露相关工具集合，减少无关 Tool 干扰，提高调用准确性。

```
用户输入
    ↓
Intent Filter (规则匹配)
    ↓
过滤后的工具集合
    ↓
Agent Tool Calling
```

### Agent 工具调用治理

- **重复调用检测**：避免同一工具被多次调用
- **搜索轮次控制**：限制搜索类工具的调用次数
- **工具调用结果检查**：评估调用结果的有效性
- **报告完整性检查**：确保分析报告覆盖必要维度

### 会话记忆系统

- **用户画像管理**：记录用户偏好和交互习惯
- **对话摘要压缩**：长对话自动压缩，保持上下文连贯
- **Redis 会话缓存**：72h Token 有效期，多级缓存策略

### 养基宝数据集成

通过 API 集成用户基金账户数据：

- 扫码授权获取账户 Token
- 查询基金账户列表
- 获取持仓明细和收益数据
- 数据事务写入 PostgreSQL
- Redis 缓存提高查询效率

实现从通用金融分析到个人资产分析的能力扩展。

### SSE 流式架构

- **后端**：Flux 累积全文 → 流式输出
- **前端**：ReadableStream 解析 → 实时渲染
- **体验**：用户可实时看到 Agent 执行过程

## 技术架构

### Backend

- **Java 17** + **Spring Boot 3.5**
- **Spring AI 1.0** - LLM 接入、Tool Calling、Prompt 管理
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
src/main/java/com/xiaomo/agent/
├── agent/              # AI Agent 核心（意图识别、工具调用）
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

### 方式一：Docker Compose 部署（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/liukai-code/xiaomo-investment-agent.git
cd xiaomo-investment-agent

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 API Key 等配置

# 3. 启动服务
docker-compose up -d

# 4. 访问应用
open http://localhost:4545
```

### 方式二：本地开发

**环境要求**：Java 17+, Node.js 18+, PostgreSQL 14+, Redis 6+

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
- [x] 深度分析 Workflow
- [x] Agent 工具调用治理
- [ ] RAG 投研知识库
- [ ] 投资组合管理
- [ ] 回测系统
- [ ] 多 Agent 协作

## 当前限制

- 当前深度分析采用预定义 Workflow，不支持完全自主任务规划
- 金融数据来自第三方接口，存在稳定性限制
- 分析结果仅作为研究辅助，不构成投资建议

## Disclaimer

小墨用于金融信息查询和投资研究辅助，不提供投资建议，不代表任何买卖推荐。股市有风险，投资需谨慎。

## License

[Apache License 2.0](LICENSE)

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI Agent 框架
- [a-stock-data](https://github.com/simonlin1212/a-stock-data) - A 股数据工具参考
