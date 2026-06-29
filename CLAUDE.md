# CLAUDE.md

## Project Overview

金融投资导学 AI 助手"小墨" — Spring Boot 3.5 + Spring AI 1.0 + Vue 3 全栈应用。

核心能力：AI Agent 对话（同步/SSE 流式）、工具调用（金融计算、行情查询、网页抓取、SQL 查询、文件操作）、MCP 外部服务集成（百度 AI 搜索）。

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.5, Spring AI 1.0 (Anthropic), Spring Data JPA + PostgreSQL, Spring Data Redis
- **Frontend**: Vue 3, TypeScript, Vite 6, Pinia, Vue Router 4, marked + highlight.js, DOMPurify
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
src/main/java/com/itlk/myclaudecode/
├── agent/service/impl/AgentLoopImpl.java   # Core: AI chat loop, context management, tool registration
├── auth/                                    # Token auth (Redis-backed, 72h TTL, single-session)
├── common/config/                           # WebMvcConfig, RedisConfig, McpKeepAliveService
├── common/exception/                        # GlobalExceptionHandler
├── conversation/                            # Conversation + ChatMessage CRUD, Redis caching
├── tool/                                    # 7 AI tools (see Tools section)
└── user/                                    # User entity + repository

frontend/src/
├── api/chat.ts              # SSE streaming client (fetch + ReadableStream)
├── stores/                  # Pinia: auth, chat, theme
├── views/ChatView.vue       # Main chat UI
├── components/blocks/       # Markdown rendering components
└── composables/useMarkdownBlocks.ts  # Streaming-friendly markdown parser
```

## Tools (tool/ package)

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

Local dev: copy `application.yml.example` to `src/main/resources/application-local.yml` (gitignored).

## API Endpoints

Auth: `POST /api/auth/{register,login,logout}`, `GET /api/auth/me`
Agent: `GET/POST /agent/conversation/*`, `GET /agent/chat`, `GET /agent/chat/stream`
Response: `{ code: 1|0, msg, data }`

## Documentation

项目文档统一放在 `docs/` 目录下，按子目录分类：`APIDocs/`、`DevelopmentProcess/`、`Guides/`、`Planning/`。

## Conventions

- Commit messages: 中文，带 prefix（feat/fix/refactor/docs），禁止自动附加 Co-Authored-By trailer
- 不要自动执行 `git push`，commit 完成后由用户自行决定是否推送
- 包名全小写，类名大驼峰
- 统一响应体: `Result<T>`
- 前端密码传输: 客户端 SHA-256 → 服务端 BCrypt
- 流式输出: 后端 Flux 累积全文，前端 SSE 解析替换
