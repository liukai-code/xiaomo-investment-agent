# 小墨 - 金融投资导学 AI 助手

<div align="center">
  <img src="docs/pic/img_1.png" alt="小墨对话界面" width="80%">
  <p><em>智能金融投资对话界面 - 自然语言驱动的投资分析</em></p>
</div>

基于 Spring Boot 3.5 + Spring AI 1.0 + Vue 3 的全栈 AI Agent 应用，专注于金融投资领域的智能问答与数据分析。

## 项目定位

小墨是一个面向金融投资者的 AI 助手，通过自然语言交互提供专业的投资分析、数据查询和决策支持。项目整合了多源金融数据，结合大语言模型能力，为用户提供智能化的投资导学服务。

## 界面预览

<div align="center">
  <img src="docs/pic/login-page.png" alt="登录页面" width="45%">
  <img src="docs/pic/img_1.png" alt="对话界面" width="45%">
</div>

## 核心功能

### AI Agent 对话
- 支持同步和 SSE 流式两种对话模式
- 上下文感知的多轮对话
- 智能意图识别和工具调用

### 工具调用能力
- **金融计算**：复利、贷款、NPV、IRR、夏普比率等 22 种计算工具
- **行情查询**：A 股、港股、美股实时行情和 K 线数据
- **数据查询**：数据库 SQL 查询（只读）
- **网页抓取**：自动抓取网页内容进行分析
- **文件操作**：读写项目文件

### A 股数据工具集
集成 13 个数据源、40 个端点的 A 股全栈数据工具包：
- **行情层**：通达信 + 腾讯财经 + 百度 K 线，实时行情不封 IP
- **研报层**：东财 + 同花顺 + iwencai，个股/行业研报 + PDF 下载
- **信号层**：同花顺热点 + 北向资金 + 龙虎榜 + 解禁预警
- **资金面**：融资融券 + 大宗交易 + 股东户数 + 分红送转
- **新闻层**：东财个股新闻 + 全球资讯
- **基础数据**：mootdx 财务 + 东财 F10 + 新浪三表
- **公告层**：巨潮公告
- **打板层**：涨停池/连板梯队/炸板率/跌停
- **期权层**：ETF 期权 T 型报价/希腊字母/隐含波动率
- **舆情互动**：互动易问答/热榜/人气榜

### MCP 外部服务集成
- 百度 AI 搜索服务
- 可扩展的 MCP 协议支持

## 技术栈

### 后端
- Java 17 + Spring Boot 3.5
- Spring AI 1.0 (Anthropic Claude)
- Spring Data JPA + PostgreSQL
- Spring Data Redis
- MCP 协议集成

### 前端
- Vue 3 + TypeScript
- Vite 6 构建工具
- Pinia 状态管理
- Vue Router 4
- lucide-vue-next 图标库
- marked + highlight.js + DOMPurify (Markdown 渲染)

### 基础设施
- PostgreSQL 数据库
- Redis 缓存
- Docker 多阶段构建 (Node 20 + JDK 17 + JRE 17)

## 使用示例

### 1. 股票行情查询
```
用户: 帮我查一下贵州茅台(600519)的实时行情
小墨: 正在查询贵州茅台的实时行情...
      当前价格: 1800.00 元
      涨跌幅: +2.5%
      成交量: 12,345 手
      市盈率: 35.2
```

### 2. 投资分析
```
用户: 分析一下宁德时代(300750)的投资价值
小墨: 正在分析宁德时代的投资价值...
      基本面分析:
      - 市盈率: 45.2 (行业平均: 38.5)
      - 净利润增长率: 25.3%
      - ROE: 18.7%
      
      技术面分析:
      - 近期走势: 上涨趋势
      - 支撑位: 450.00 元
      - 压力位: 520.00 元
      
      风险提示: 新能源行业政策风险
```

### 3. 金融计算
```
用户: 计算贷款 100 万，利率 4.5%，期限 30 年的月供
小墨: 正在计算贷款月供...
      贷款金额: 1,000,000 元
      年利率: 4.5%
      贷款期限: 30 年 (360 个月)
      月供: 5,066.85 元
      总利息: 824,066.00 元
      还款总额: 1,824,066.00 元
```

### 4. A 股数据查询
```
用户: 查询今天涨停的股票有哪些
小墨: 正在查询今日涨停股票...
      今日涨停股票 (共 25 只):
      1. 贵州茅台 (600519) - 涨停原因: 业绩预增
      2. 宁德时代 (300750) - 涨停原因: 新能源政策利好
      ...
      
      连板梯队:
      - 3 连板: 5 只
      - 2 连板: 8 只
      - 首板: 12 只
```

## 项目亮点

### 1. 智能工具调用
AI 能够根据用户意图自动选择合适的工具，支持复杂的投资分析场景。

### 2. 多源数据整合
整合 13 个金融数据源，提供 40 个数据端点，覆盖 A 股市场的各个维度。

### 3. 流式实时交互
SSE 流式输出提供实时的对话体验，支持长文本生成。

### 4. 安全可靠
- Token 认证 + Redis 会话管理
- SQL 查询白名单机制
- 文件操作目录限制
- 敏感信息环境变量化

### 5. 高性能架构
- Redis 多级缓存策略
- 数据库查询优化
- 异步处理长时间任务

### 6. 现代化前端
- Vue 3 Composition API
- TypeScript 类型安全
- 响应式设计
- 优雅的 Markdown 渲染

## 快速开始

### 环境要求
- Java 17+
- Node.js 18+
- PostgreSQL 14+
- Redis 6+

### 1. 克隆项目
```bash
git clone https://github.com/your-username/my-claude-code.git
cd my-claude-code
```

### 2. 配置环境变量
复制 `.env.example` 到 `.env` 文件，配置以下参数：

```bash
# 数据库配置
DB_USER=postgres
DB_PASSWORD=your_postgres_password
DB_NAME=myclaudecode

# Redis 配置
REDIS_PASSWORD=your_redis_password

# AI 模型配置
ANTHROPIC_API_KEY=your_api_key
ANTHROPIC_BASE_URL=https://api.anthropic.com

# MCP 服务配置（可选）
DASHSCOPE_MCP_URL=

# 安全配置
CONFIG_ENCRYPTION_KEY=
ADMIN_PASSWORD=admin123
```

**说明：**
- 数据库和 Redis 使用本地已有的实例
- AI 模型需要 Anthropic API 密钥
- MCP 服务配置用于百度 AI 搜索等外部服务
- 管理员密码用于后台管理（可选）

### 3. 启动后端
```bash
# 构建项目
mvn clean package -DskipTests

# 运行应用 (端口 4545)
mvn spring-boot:run
```

### 4. 启动前端
```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器 (端口 5656，代理到 4545)
npm run dev
```

### 5. 访问应用
打开浏览器访问 `http://localhost:5656`，注册账号后即可开始使用。

## 项目结构

```
src/main/java/com/itlk/myclaudecode/
├── agent/service/impl/AgentLoopImpl.java   # AI 对话核心，上下文管理和工具注册
├── auth/                                   # Token 认证 (Redis 支持，72 小时 TTL，单会话)
├── common/config/                          # WebMvc、Redis、MCP 保活等配置
├── common/exception/                       # 全局异常处理
├── conversation/                           # 会话和消息 CRUD，Redis 缓存
├── tool/                                   # AI 工具集
│   ├── FinancialCalcTool.java              # 22 种金融计算工具
│   ├── FinancialDataTool.java              # 行情查询工具
│   ├── SqlTool.java                        # SQL 查询工具
│   ├── WebFetchTool.java                   # 网页抓取工具
│   ├── FileReadTool.java                   # 文件读取工具
│   ├── FileWriteTool.java                  # 文件写入工具
│   ├── FileListTool.java                   # 文件列表工具
│   └── astock/                             # A 股数据工具集 (8 个 Router Tool)
│       ├── AStockQuoteRouterTool.java      # 行情层
│       ├── AStockReportRouterTool.java     # 研报层
│       ├── AStockSignalRouterTool.java     # 信号层
│       ├── AStockCapitalRouterTool.java    # 资金面
│       ├── AStockNewsRouterTool.java       # 新闻层
│       ├── AStockLimitUpRouterTool.java    # 打板层
│       ├── AStockOptionRouterTool.java     # 期权层
│       └── AStockSentimentRouterTool.java  # 舆情互动层
└── user/                                   # 用户实体和仓储

frontend/src/
├── api/chat.ts              # SSE 流式客户端 (fetch + ReadableStream)
├── stores/                  # Pinia 状态管理: auth, chat, theme
├── views/ChatView.vue       # 主聊天界面
├── components/blocks/       # Markdown 渲染组件
└── composables/useMarkdownBlocks.ts  # 流式友好 Markdown 解析器
```

## API 文档

### 认证接口
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出
- `GET /api/auth/me` - 获取当前用户信息

### Agent 接口
- `GET /agent/conversation/*` - 获取会话信息
- `POST /agent/conversation/*` - 创建会话
- `GET /agent/chat` - 同步对话
- `GET /agent/chat/stream` - SSE 流式对话

### 响应格式
```json
{
  "code": 1,
  "msg": "success",
  "data": { ... }
}
```

## 测试

### 运行测试
```bash
# 后端测试
mvn test

# 前端测试
cd frontend
npm run test
```

### 测试规范
- 禁止 `assertNotNull` 反模式，必须验证业务数据
- 使用 Mock 对象时必须注入真实 JSON 响应
- 覆盖错误场景：API 返回 403、空数据、网络异常等
- 数据查询类测试必须断言具体业务字段

## 部署

### Docker 部署
```bash
# 确保已安装 Docker 和 Docker Compose

# 配置环境变量（创建 .env 文件）
cp .env.example .env
# 编辑 .env 文件，配置数据库、Redis 和 AI 模型参数

# 构建并启动应用容器
docker-compose up --build

# 应用将运行在 4545 端口
# 前端构建后部署到 src/main/resources/static/
```

### 生产环境部署
1. 构建后端 JAR 包: `mvn clean package -DskipTests`
2. 构建前端静态资源: `cd frontend && npm run build`
3. 配置 Nginx 反向代理，将请求转发到 4545 端口
4. 配置 PostgreSQL 和 Redis 生产环境
5. 设置环境变量或配置文件

## 常见问题

### Q: 如何获取 Anthropic API 密钥？
A: 访问 [Anthropic 官网](https://www.anthropic.com/) 注册账号并获取 API 密钥。

### Q: 支持哪些 AI 模型？
A: 目前支持 Anthropic Claude 系列模型，包括 Claude 3.5 Sonnet、Claude 3 Haiku 等。

### Q: A 股数据工具需要付费吗？
A: 大部分数据源免费，仅 iwencai 语义搜索需要 API 密钥（可选）。

### Q: 如何扩展新的工具？
A: 在 `src/main/java/com/itlk/myclaudecode/tool/` 目录下创建新的工具类，实现 `@Tool` 注解即可。

### Q: 生产环境部署需要注意什么？
A: 需要配置 HTTPS、设置强密码、配置防火墙、定期备份数据库等。

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### Commit 规范
- 使用中文 commit message
- 带 prefix: `feat` / `fix` / `refactor` / `docs`
- 禁止自动附加 Co-Authored-By trailer

## 许可证

本项目基于 Apache License 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 项目链接: https://github.com/your-username/my-claude-code
- 问题反馈: https://github.com/your-username/my-claude-code/issues

## 更新日志

### v1.2.0 (2026-07-22)
- 新增设置页面：记忆开关、对话偏好、账户信息
- 支持 temperature/maxTokens/上下文窗口可调
- 数学公式支持 LaTeX 分隔符
- 记忆功能单元测试通过

### v1.1.0 (2026-07-15)
- 新增小墨记忆功能：用户画像记忆 + 对话摘要压缩
- 优化深度分析工作流
- 修复多个意图识别问题

### v1.0.0 (2026-07-01)
- 初始版本发布
- AI Agent 对话功能
- A 股数据工具集集成
- 金融计算工具
- MCP 外部服务支持

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI 集成框架
- [Vue.js](https://vuejs.org/) - 前端框架
- [Anthropic Claude](https://www.anthropic.com/) - AI 模型支持
- [a-stock-data](https://github.com/simonlin1212/a-stock-data) - A 股数据工具包灵感来源

---

**小墨** - 让金融投资更智能、更简单。