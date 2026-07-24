# CLAUDE.md

## Project Overview

金融投资导学 AI 助手"小墨" — Spring Boot 3.5 + Spring AI 1.0 + Vue 3 全栈应用。

核心能力：AI Agent 对话（同步/SSE 流式）、工具调用（金融计算、行情查询、网页抓取、SQL 查询、文件操作）、MCP 外部服务集成（百度 AI 搜索）。

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.5, Spring AI 1.0 (Anthropic), Spring Data JPA + PostgreSQL, Spring Data Redis
- **Frontend**: Vue 3, TypeScript, Vite 6, Pinia, Vue Router 4, lucide-vue-next, marked + highlight.js, DOMPurify
- **Infra**: PostgreSQL, Redis, Docker (multi-stage build: Node 20 + JDK 17 + JRE 17)

## Build & Run

```bash
# Backend
mvn clean package -DskipTests    # build
mvn spring-boot:run              # run (port 4545)
mvn test                         # run tests

# Frontend
cd frontend
npm install && npm run dev       # dev server (port 5656, proxies to :4545)
npm run build                    # build to src/main/resources/static/

# Docker (full stack)
docker-compose up --build        # app + PostgreSQL + Redis
```

## Project Structure

```
src/main/java/com/xiaomo/agent/
├── agent/service/impl/AgentLoopImpl.java   # Core: AI chat loop, context management, tool registration
├── auth/                                    # Token auth (Redis-backed, 72h TTL, single-session)
├── common/config/                           # WebMvcConfig, RedisConfig, McpKeepAliveService, HttpClientService
├── common/exception/                        # GlobalExceptionHandler
├── conversation/                            # Conversation + ChatMessage CRUD, Redis caching
├── tool/                                    # AI tools (see Tools section)
│   └── astock/                              # A股数据工具集 (8 Router Tools + EastMoneyRateLimiter)
└── user/                                    # User entity + repository

frontend/src/
├── api/chat.ts              # SSE streaming client (fetch + ReadableStream)
├── stores/                  # Pinia: auth, chat, theme
├── views/ChatView.vue       # Main chat UI
├── components/blocks/       # Markdown rendering components
└── composables/useMarkdownBlocks.ts  # Streaming-friendly markdown parser
```

## Tools (tool/ package)

### 基础工具

| Tool | Methods | Notes |
|------|---------|-------|
| FinancialCalcTool | 22 methods: compoundInterest, loanPayment, npv, irr, sharpeRatio 等 | BigDecimal 精度 |
| FinancialDataTool | getAShareQuote, getHKStockQuote, getUSStockQuote, getFundNav, searchStockByName | 腾讯/东方财富 API |
| SqlTool | getDatabaseSchema, executeQuery | SELECT only, 禁止危险关键词 |
| WebFetchTool | fetchWebpage, fetchArticleContent | max 20K chars, 屏蔽内网 IP |
| FileReadTool | read_file | max 10MB, 限制项目目录 |
| FileWriteTool | write_file, append_file | 限制项目目录 |
| FileListTool | list_files | 支持递归和 glob |
| MCP Tools | Baidu AI Search | via ToolCallbackProvider, 60s 保活 |

### A股数据工具集 (tool/astock/)

基于 [a-stock-data](docs/a-stock-data/) 移植，44个数据端点 → 8个Router Tool。东财接口统一走 `EastMoneyRateLimiter`（串行限流 ≥1s + 随机抖动）。

| Tool | Operations | 数据源 |
|------|-----------|--------|
| AStockQuoteRouterTool | tencentQuote, baiduKline | 腾讯/百度 |
| AStockReportRouterTool | stockReport, industryReport, downloadReportPdf, thsEpsForecast, iwencaiSearch, iwencaiQuery | 东财/同花顺/iwencai |
| AStockSignalRouterTool | conceptBlocks, fundFlowMinute, dragonTigerBoard, dailyDragonTiger, lockupExpiry, industryRanking | 东财 push2/datacenter |
| AStockCapitalRouterTool | marginTrading, blockTrade, holderNumChange, dividendHistory, fundFlow120d, northboundFlow | 东财 datacenter/Redis |
| AStockNewsRouterTool | stockNews, globalNews, cninfoAnnouncements, irmQA, sinaFinancialReport | 东财/巨潮/新浪 |
| AStockLimitUpRouterTool | ztPool, zbPool, dtPool, yztPool, thsLimitUpPool, sentimentOverview | 东财 push2ex/同花顺 |
| AStockOptionRouterTool | optionCodes, optionTQuote, optionGreeks | 新浪 |
| AStockSentimentRouterTool | thsHotList, emHotRank, emConceptHit | 同花顺/东财 |

## Key Configuration

- Server port: **4545**, Frontend dev: **5656**
- AI model: `mimo-v2.5-pro`, max-tokens: 4096, temperature: 0.7
- Context window: 最近 50 条消息
- SSE 流式超时: 120s
- Redis 缓存: 会话列表 30min, 消息 1h, 最近消息 10min

## Environment Variables

| Variable | Description |
|----------|-------------|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | DB credentials |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis config |
| `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL` | AI model API |
| `BAIDU_MCP_URL` / `BAIDU_MCP_SSE_ENDPOINT` | Baidu MCP |
| `IWENCAI_API_KEY` | iwencai NL语义搜索 API Key（可选） |

Local dev: copy `application.yml.example` to `src/main/resources/application-local.yml` (gitignored).

## API Endpoints

Auth: `POST /api/auth/{register,login,logout}`, `GET /api/auth/me`
- register/login 接收 `{email, password}`，register 返回 `{id, email, accountId}`，login 返回 `{token, userId, email, accountId}`
- me 返回 `{id, email, accountId}`
- 注册时自动生成唯一六位数字账号 `user_123456` 格式入库

Agent: `GET/POST /agent/conversation/*`, `GET /agent/chat`, `GET /agent/chat/stream`
Response: `{ code: 1|0, msg, data }`

## Documentation

项目文档统一放在 `docs/` 目录下，按子目录分类：`APIDocs/`、`DevelopmentProcess/`、`Guides/`、`Planning/`。

## Conventions

- 使用中文回答用户问题，代码注释和 commit message 也用中文
- Commit messages: 中文，带 prefix（feat/fix/refactor/docs），禁止自动附加 Co-Authored-By trailer
- 不要自动执行 `git push`，commit 完成后由用户自行决定是否推送
- 包名全小写，类名大驼峰
- 统一响应体: `Result<T>`
- 前端密码传输: 客户端 SHA-256 → 服务端 BCrypt
- 流式输出: 后端 Flux 累积全文，前端 SSE 解析替换
- 前端图标统一使用 `lucide-vue-next`，不使用其他图标库
- 每次修改或新增后端接口，必须 review 代码逻辑并编写真实测试用例自测通过后才算完成

## 测试规范

### 禁止糊弄式测试

写测试用例必须验证真实行为，禁止以下做法：

1. **禁止 `assertNotNull` 反模式** — `assertNotNull(result)` 不能证明功能正确，工具返回的错误字符串也是非null的。必须断言返回内容包含预期的业务数据（如股票名称、价格、板块名等）。
2. **禁止无 stub 的 Mock** — 使用 `@Mock HttpClientService` 或 `@Mock EastMoneyRateLimiter` 时，必须用 `when(...).thenReturn(...)` 注入真实的 JSON 响应，验证工具的解析逻辑。Mock 返回 null 时工具会吞掉异常返回错误字符串，`assertNotNull` 永远通过。
3. **禁止只测 happy path** — 必须覆盖：API返回403、空数据、网络异常、缺少参数等错误场景。

### 测试断言标准

- 数据查询类测试：断言返回内容包含具体的业务字段（股票名称、价格、板块、日期等）
- 错误处理类测试：断言返回内容包含明确的错误提示关键词
- 路由/参数类测试：断言返回内容包含"操作类型不能为空"、"缺少参数"等提示

### Mock 策略

- `HttpClientService`：mock `get()` 和 `getWithJdkClient()`，注入真实 JSON 响应
- `EastMoneyRateLimiter`：mock `get()` 和 `post()`，注入真实 JSON 响应
- 对于可能不被调用的 stub，使用 `lenient().when(...)` 避免 UnnecessaryStubbingException
- 每个工具类必须有对应的测试文件，覆盖所有 operation 路由
