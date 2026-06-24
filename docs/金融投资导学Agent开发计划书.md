# 金融投资导学 Agent 开发计划书

> **项目名称：** 金融投资导学 Agent
> **技术栈：** Spring Boot 3.5 + Spring AI 1.0 + PostgreSQL(PgVector) + 阿里通义 Embedding
> **创建日期：** 2026-06-17
> **版本：** v1.0

---

## 一、项目概述

### 1.1 项目目标

构建一个**金融投资领域导学 Agent**，用户可通过自然语言提问，Agent 基于金融知识库（书籍、教材内容）进行智能问答，辅助用户学习金融投资知识。

### 1.2 核心能力

| 能力 | 说明 |
|------|------|
| 知识问答 | 基于上传的金融书籍内容回答用户问题 |
| 智能检索 | LLM 自主判断是否需要调用 RAG 检索 |
| 引用溯源 | 每个回答标注来源书籍和章节 |
| 多轮对话 | 支持上下文连续对话 |
| 流式输出 | 支持 SSE 流式返回回答 |

### 1.3 技术架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    用户请求 (HTTP)                        │
└──────────────────────────┬──────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────┐
│              Agent Controller (REST API)                 │
│         /agent/chat    /agent/chat/stream                │
└──────────────────────────┬──────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────┐
│               Agent Core (AgentLoopImpl)                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ 会话管理     │  │  LLM 推理   │  │  Tool 调度       │  │
│  │ (Session)    │  │ (Anthropic) │  │ (Function Call) │  │
│  └─────────────┘  └──────┬──────┘  └────────┬────────┘  │
│                          │                  │            │
│                          ▼                  ▼            │
│              ┌───────────────────────┐                  │
│              │  Tool: 知识库检索      │                  │
│              │  (FinancialKnowledge) │                  │
│              └───────────┬───────────┘                  │
└──────────────────────────┼──────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    RAG Pipeline                          │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ 文档加载  │→ │ 分块 & 向量化 │→ │  PgVector 存储     │  │
│  │ (Tika)   │  │ (Splitter)   │  │  (PostgreSQL)     │  │
│  └──────────┘  └──────────────┘  └───────────────────┘  │
│                                                         │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ 用户查询  │→ │ Query 向量化  │→ │  相似度检索 TopK   │  │
│  └──────────┘  └──────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 二、技术选型详情

### 2.1 核心依赖

| 组件 | 技术选型 | 版本 | 用途 |
|------|----------|------|------|
| 框架 | Spring Boot | 3.5.0 | Web 应用框架 |
| AI 框架 | Spring AI | 1.0.0 | LLM 集成 |
| LLM | Anthropic (mimo-v2.5-pro) | - | 对话推理 |
| Embedding | 阿里 text-embedding-v3 | - | 文本向量化 |
| 向量数据库 | PgVector | 0.8.0 | 向量存储与检索 |
| 文档解析 | Apache Tika | 2.9.2 | PDF/Word/TXT 解析 |
| 数据库 | PostgreSQL | 15+ | 结构化数据 + 向量存储 |
| 工具库 | Lombok | - | 简化代码 |

### 2.2 Maven 依赖清单

```xml
<!-- PostgreSQL + PgVector -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>

<!-- 阿里通义 Embedding (通过 OpenAI 兼容接口) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<!-- 文档解析 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>

<!-- PostgreSQL 驱动 -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## 三、数据库设计

### 3.1 PgVector 向量表

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 文档块向量表 (Spring AI 默认表结构)
CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT NOT NULL,
    metadata  JSONB DEFAULT '{}',
    embedding vector(1024)  -- text-embedding-v3 输出 1024 维
);

-- 创建向量索引 (HNSW 算法，检索速度最优)
CREATE INDEX ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

### 3.2 文档管理表

```sql
-- 书籍/文档元信息
CREATE TABLE financial_documents (
    id          BIGSERIAL PRIMARY KEY,
    file_name   VARCHAR(500) NOT NULL,
    file_type   VARCHAR(50) NOT NULL,        -- pdf/txt/docx
    title       VARCHAR(500),                 -- 书名/文档标题
    author      VARCHAR(200),                 -- 作者
    category    VARCHAR(100),                 -- 分类：教材/法规/研报
    file_size   BIGINT,
    chunk_count INT DEFAULT 0,                -- 分块数量
    status      VARCHAR(20) DEFAULT 'PENDING', -- PENDING/PROCESSING/COMPLETED/FAILED
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- 文档分块记录
CREATE TABLE document_chunks (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT REFERENCES financial_documents(id),
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    page_number     INT,                      -- 页码（PDF）
    chapter         VARCHAR(200),             -- 章节标题
    token_count     INT,
    vector_store_id VARCHAR(255),             -- 关联 vector_store.id
    created_at      TIMESTAMP DEFAULT NOW()
);
```

---

## 四、代码结构设计

### 4.1 目录结构

```
src/main/java/com/itlk/myclaudecode/
├── MyClaudeCodeApplication.java
│
├── agent/                              # [已有] Agent 核心
│   ├── config/
│   │   └── AnthropicConfig.java        # [已有] LLM 配置
│   ├── controller/
│   │   └── AgentLoopController.java    # [修改] 增加文档管理接口
│   ├── entity/
│   │   └── Result.java                 # [已有] 响应封装
│   └── service/
│       ├── AgentLoop.java              # [已有] Agent 接口
│       └── Impl/
│           └── AgentLoopImpl.java      # [重构] 集成 Tool 系统
│
├── rag/                                # [新增] RAG 模块
│   ├── config/
│   │   └── RagConfig.java              # RAG 相关 Bean 配置
│   ├── loader/
│   │   ├── DocumentLoader.java         # 文档加载接口
│   │   └── TikaDocumentLoader.java     # Tika 实现
│   ├── splitter/
│   │   ├── DocumentSplitter.java       # 分块接口
│   │   └── ChineseSemanticSplitter.java # 中文语义分块
│   ├── store/
│   │   └── VectorStoreService.java     # 向量存储服务
│   └── retriever/
│       └── FinancialRetriever.java     # 金融领域检索服务
│
├── tool/                               # [新增] Tool 体系
│   ├── FinancialKnowledgeTool.java     # RAG 知识检索 Tool
│   └── ToolRegistry.java              # Tool 注册中心
│
├── document/                           # [新增] 文档管理
│   ├── controller/
│   │   └── DocumentController.java     # 文档上传/管理 API
│   ├── entity/
│   │   ├── FinancialDocument.java      # 文档实体
│   │   └── DocumentChunk.java          # 分块实体
│   ├── repository/
│   │   ├── DocumentRepository.java     # 文档 JPA Repository
│   │   └── ChunkRepository.java        # 分块 JPA Repository
│   └── service/
│       ├── DocumentService.java        # 文档管理服务
│       └── IngestionService.java       # 文档入库服务（加载→分块→向量化）
│
└── config/
    └── WebConfig.java                  # [新增] CORS 等全局配置
```

### 4.2 核心类职责

| 类 | 职责 |
|----|------|
| `DocumentController` | 提供文档上传、列表、删除接口 |
| `IngestionService` | 文档入库流程：加载 → 解析 → 分块 → 向量化 → 存储 |
| `VectorStoreService` | 封装向量存储的增删查操作 |
| `FinancialRetriever` | 金融领域检索，支持元数据过滤 |
| `FinancialKnowledgeTool` | 供 LLM 调用的 RAG Tool，封装检索逻辑 |
| `AgentLoopImpl` | 重构后集成 Tool 系统，管理会话 |
| `ToolRegistry` | 注册所有可用 Tool，供 Agent 调度 |

---

## 五、分阶段开发计划

---

### Phase 0: 环境准备（0.5天）

**目标：** 搭建基础环境，确保数据库和依赖可用。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 0.1 | 安装 PostgreSQL 15+ | 本地或 Docker 部署 | 可连接的数据库实例 |
| 0.2 | 安装 pgvector 扩展 | `CREATE EXTENSION vector;` | pgvector 可用 |
| 0.3 | 创建数据库和表 | 执行上述 SQL 建表语句 | 表结构就绪 |
| 0.4 | 申请阿里云 API Key | 用于 text-embedding-v3 | API Key 可用 |
| 0.5 | 更新 pom.xml | 添加 PgVector、Tika、OpenAI 依赖 | 编译通过 |
| 0.6 | 更新 application.yml | 添加 PgVector、Embedding 配置 | 配置就绪 |

#### application.yml 配置示例

```yaml
spring:
  # 数据库
  datasource:
    url: jdbc:postgresql://localhost:5432/financial_agent
    username: postgres
    password: your_password

  # Spring AI - PgVector
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1024
        table-name: vector_store

    # 阿里通义 Embedding (OpenAI 兼容接口)
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${ALI_API_KEY}
      embedding:
        options:
          model: text-embedding-v3
```

---

### Phase 1: 文档入库管道（2-3天）

**目标：** 实现文档上传 → 解析 → 分块 → 向量化 → 存储的完整流程。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 1.1 | 实体类 | `FinancialDocument` + `DocumentChunk` JPA 实体 | 数据模型 |
| 1.2 | Repository 层 | `DocumentRepository` + `ChunkRepository` | 数据访问 |
| 1.3 | `TikaDocumentLoader` | 使用 Tika 解析 PDF/TXT/DOCX | 文档解析能力 |
| 1.4 | `ChineseSemanticSplitter` | 中文语义分块，按段落/标题切分 | 分块能力 |
| 1.5 | `VectorStoreService` | 封装 Spring AI PgVectorStore | 向量存储能力 |
| 1.6 | `IngestionService` | 编排整个入库流程 | 入库管道 |
| 1.7 | `DocumentController` | 上传接口 `POST /document/upload` | API 接口 |
| 1.8 | 单元测试 | 分块逻辑测试、入库流程测试 | 测试通过 |

#### 关键代码设计

**IngestionService 流程：**

```
upload(file)
  │
  ├─ 1. 保存文件元信息到 financial_documents 表 (status=PENDING)
  │
  ├─ 2. TikaDocumentLoader.load(file)
  │     └─ 解析为 List<Document> (Spring AI Document)
  │
  ├─ 3. ChineseSemanticSplitter.split(documents)
  │     └─ 按中文段落语义分块，每块 300-500 token
  │
  ├─ 4. 为每个 chunk 生成唯一 ID，存入 document_chunks 表
  │
  ├─ 5. VectorStoreService.add(chunks)
  │     └─ 调用阿里 Embedding API 向量化 → 存入 PgVector
  │
  └─ 6. 更新 financial_documents.status = COMPLETED
```

**ChineseSemanticSplitter 分块策略：**

```java
分块规则：
1. 优先按自然段落分割
2. 保持标题与正文的关联性（标题块 + 内容块合并）
3. 每块目标大小：300-500 token
4. 相邻块重叠：50 token（保持上下文连贯）
5. 不在句子中间切断

特殊处理：
- 表格内容：整表作为一个块
- 列表内容：保持列表项完整性
- 金融公式：与上下文一起，不单独切分
```

#### API 设计

```
POST /document/upload
Content-Type: multipart/form-data
参数：
  - file: 文件 (PDF/TXT/DOCX, 最大 50MB)
  - title: 书名/文档标题
  - category: 分类 (教材/法规/研报)
  - author: 作者 (可选)
返回：
  - documentId: 文档ID
  - status: 处理状态
  - message: 提示信息

GET /document/list
返回：文档列表

GET /document/{id}/status
返回：文档处理状态和分块数量

DELETE /document/{id}
说明：删除文档及其所有分块和向量数据
```

---

### Phase 2: RAG 检索服务（1-2天）

**目标：** 实现基于向量的语义检索，支持元数据过滤。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 2.1 | `FinancialRetriever` | 核心检索逻辑，查询向量化 + 相似度搜索 | 检索服务 |
| 2.2 | 检索结果排序 | 向量相似度 + 元数据权重综合排序 | 排序逻辑 |
| 2.3 | 元数据过滤 | 支持按 category、document_id 过滤 | 过滤能力 |
| 2.4 | 检索结果格式化 | 返回内容 + 来源引用信息 | 格式化输出 |
| 2.5 | 检索测试 | 验证检索效果和准确性 | 测试通过 |

#### 关键代码设计

**FinancialRetriever 检索流程：**

```
search(query, category?, topK=5)
  │
  ├─ 1. 构建 SearchRequest
  │     ├─ query: 用户查询文本
  │     ├─ topK: 返回 Top K 个结果
  │     ├─ similarityThreshold: 最低相似度阈值 (0.7)
  │     └─ filterExpression: 元数据过滤条件
  │
  ├─ 2. 调用 PgVectorStore.similaritySearch(request)
  │     └─ 自动调用阿里 Embedding 向量化查询 → PgVector 检索
  │
  ├─ 3. 结果后处理
  │     ├─ 按相似度排序
  │     ├─ 补充元数据（书名、章节、页码）
  │     └─ 去重（同一段落不重复返回）
  │
  └─ 4. 返回 List<RetrievalResult>
        └─ 每个结果包含: content, score, source, page, chapter
```

**检索结果数据结构：**

```java
public class RetrievalResult {
    private String content;      // 检索到的文本内容
    private double score;        // 相似度分数 (0-1)
    private String source;       // 来源书籍/文档名
    private String chapter;      // 章节
    private Integer pageNumber;  // 页码
    private String category;     // 分类
}
```

---

### Phase 3: Tool 系统集成（1-2天）

**目标：** 将 RAG 检索封装为 Tool，集成到 Agent 中，实现 LLM 自主决策调用。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 3.1 | `FinancialKnowledgeTool` | RAG 检索 Tool，供 LLM 调用 | Tool 实现 |
| 3.2 | `ToolRegistry` | Tool 注册中心，管理所有可用 Tool | 注册机制 |
| 3.3 | 重构 `AgentLoopImpl` | 集成 Spring AI Function Calling | Agent 核心 |
| 3.4 | System Prompt 设计 | 金融导学 Agent 的系统提示词 | Prompt |
| 3.5 | 流式输出适配 | Tool 调用场景下的流式返回 | 流式支持 |
| 3.6 | 集成测试 | 端到端测试完整流程 | 测试通过 |

#### 关键代码设计

**FinancialKnowledgeTool：**

```java
@Component
public class FinancialKnowledgeTool
    implements Function<FinancialKnowledgeTool.Request, FinancialKnowledgeTool.Response> {

    @Autowired
    private FinancialRetriever retriever;

    @Override
    @JsonClassDescription("""
        金融投资知识库检索工具。
        当用户询问以下类型问题时调用：
        - 金融概念和术语解释（如：什么是ETF、什么是市盈率）
        - 投资策略和理论（如：价值投资、定投策略）
        - 法规政策解读（如：投资者适当性管理）
        - 金融历史案例（如：某次金融危机分析）
        当用户询问实时行情、个人信息、简单计算时，不要调用此工具。
        """)
    public Response apply(Request request) {
        List<RetrievalResult> results = retriever.search(
            request.query,
            request.category,
            5  // topK
        );
        return new Response(results);
    }

    public record Request(
        @JsonProperty("query") String query,
        @JsonProperty(value = "category", required = false) String category
    ) {}

    public record Response(List<RetrievalResult> results) {}
}
```

**AgentLoopImpl 重构：**

```java
@Service
public class AgentLoopImpl implements AgentLoop {

    @Autowired
    private FinancialKnowledgeTool knowledgeTool;

    private ChatModel chatModel;
    private final Map<String, List<Message>> sessionHistories = new ConcurrentHashMap<>();

    private ChatModel getChatModel() {
        if (chatModel == null) {
            synchronized (this) {
                if (chatModel == null) {
                    // 构建 AnthropicApi + ChatModel
                    // 注册 Function Tool
                    chatModel = AnthropicChatModel.builder()
                        .anthropicApi(anthropicApi)
                        .defaultOptions(AnthropicChatOptions.builder()
                            .model(modelId)
                            .function("financialKnowledge", knowledgeTool)
                            .build())
                        .build();
                }
            }
        }
        return chatModel;
    }
}
```

**金融导学 Agent System Prompt：**

```java
String SYSTEM_PROMPT = """
    你是一位专业的金融投资导学助手，帮助用户学习金融投资知识。

    ## 你的能力
    1. 回答金融投资领域的概念、理论、策略相关问题
    2. 使用知识库检索工具查找专业内容，确保回答准确有据
    3. 结合实际案例帮助用户理解抽象概念

    ## 使用规则
    - 当用户询问金融概念、投资策略、法规政策等知识性问题时，
      必须先调用 financialKnowledge 工具检索知识库
    - 基于检索结果进行回答，并引用来源
    - 当知识库没有相关内容时，诚实告知并基于通用知识回答
    - 回答要通俗易懂，适合金融初学者理解
    - 涉及具体投资建议时，必须提示风险

    ## 回答格式
    - 先给出核心答案
    - 再展开详细解释
    - 必要时举例说明
    - 标注信息来源（如有）
    """;
```

---

### Phase 4: 会话管理优化（1天）

**目标：** 修复当前共享会话的问题，实现多用户会话隔离。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 4.1 | 会话隔离 | 基于 sessionId 的独立会话历史 | 多用户支持 |
| 4.2 | 会话过期清理 | 超过30分钟未活跃的会话自动清理 | 内存管理 |
| 4.3 | 会话上下文窗口 | 限制历史消息数量，防止 token 超限 | 稳定性 |
| 4.4 | Controller 适配 | 接口支持 sessionId 参数 | API 适配 |

#### 关键代码设计

```java
// 会话管理
private final Map<String, List<Message>> sessionHistories = new ConcurrentHashMap<>();
private final Map<String, LocalDateTime> sessionLastAccess = new ConcurrentHashMap<>();

private static final int MAX_HISTORY_SIZE = 20;  // 最多保留20条消息
private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

// 获取或创建会话
private List<Message> getOrCreateSession(String sessionId) {
    cleanupExpiredSessions();
    sessionLastAccess.put(sessionId, LocalDateTime.now());
    return sessionHistories.computeIfAbsent(sessionId,
        k -> new ArrayList<>(List.of(
            new SystemMessage(SYSTEM_PROMPT)
        )));
}

// 清理过期会话
private void cleanupExpiredSessions() {
    LocalDateTime threshold = LocalDateTime.now().minus(SESSION_TIMEOUT);
    sessionLastAccess.entrySet().removeIf(entry -> {
        if (entry.getValue().isBefore(threshold)) {
            sessionHistories.remove(entry.getKey());
            return true;
        }
        return false;
    });
}
```

---

### Phase 5: 前端页面适配（1-2天）

**目标：** 适配前端页面，支持文档管理和对话界面。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 5.1 | 文档上传页面 | 支持拖拽上传 PDF/TXT/DOCX | 上传功能 |
| 5.2 | 文档列表页面 | 显示已上传文档及处理状态 | 列表展示 |
| 5.3 | 对话页面优化 | 支持 sessionId、显示引用来源 | 对话优化 |
| 5.4 | 引用来源展示 | 回答中高亮显示知识来源 | 引用展示 |

---

### Phase 6: 测试与优化（2-3天）

**目标：** 全面测试，优化检索效果和回答质量。

#### 任务清单

| # | 任务 | 说明 | 产出 |
|---|------|------|------|
| 6.1 | 准备测试数据 | 3-5本金融投资类书籍 PDF | 测试数据 |
| 6.2 | 入库测试 | 验证不同格式文档的入库效果 | 入库正常 |
| 6.3 | 检索效果测试 | 测试不同类型问题的检索准确性 | 检索评估 |
| 6.4 | Tool 调用测试 | 验证 LLM 是否正确判断调用时机 | 调用评估 |
| 6.5 | 回答质量评估 | 评估回答的准确性和完整性 | 质量报告 |
| 6.6 | 性能测试 | 检索响应时间、并发能力 | 性能指标 |
| 6.7 | Prompt 优化 | 根据测试结果优化 System Prompt | Prompt 迭代 |
| 6.8 | 分块策略优化 | 根据检索效果调整分块参数 | 策略优化 |

#### 测试用例设计

| 测试场景 | 用户问题 | 预期行为 |
|----------|----------|----------|
| 概念问答 | "什么是ETF？" | 调用 RAG → 返回解释 + 来源 |
| 策略问答 | "价值投资的核心理念是什么？" | 调用 RAG → 返回策略分析 |
| 闲聊 | "你好" | 不调用 RAG → 直接回复 |
| 实时问题 | "今天上证指数多少？" | 不调用 RAG → 告知无法查询 |
| 计算问题 | "年化收益率怎么算？" | 可能调用 RAG → 给出公式 |
| 超出范围 | "推荐一只股票" | 不推荐 → 提示风险 |

---

## 六、API 接口汇总

### 6.1 对话接口

```
POST /agent/chat
Content-Type: application/json
Body: {
    "sessionId": "user-123",
    "message": "什么是ETF？"
}
Response: {
    "code": 200,
    "data": "ETF是交易型开放式指数基金..."
}

POST /agent/chat/stream
Content-Type: application/json
Body: {
    "sessionId": "user-123",
    "message": "什么是ETF？"
}
Response: SSE stream
```

### 6.2 文档管理接口

```
POST   /document/upload        # 上传文档
GET    /document/list           # 文档列表
GET    /document/{id}           # 文档详情
GET    /document/{id}/status    # 处理状态
DELETE /document/{id}           # 删除文档

POST   /document/search         # 直接检索测试
Body: {
    "query": "什么是ETF",
    "category": "教材",
    "topK": 5
}
```

---

## 七、开发排期总览

```
Week 1:
├── Day 1       Phase 0: 环境准备 (数据库、依赖、配置)
├── Day 2-3     Phase 1: 文档入库管道 (加载→分块→向量化)
├── Day 4       Phase 2: RAG 检索服务
└── Day 5       Phase 3: Tool 系统集成

Week 2:
├── Day 1       Phase 4: 会话管理优化
├── Day 2-3     Phase 5: 前端页面适配
└── Day 4-5     Phase 6: 测试与优化

总计：约 10 个工作日
```

---

## 八、风险与应对

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| 阿里 Embedding API 延迟高 | 入库慢 | 支持批量向量化，后台异步处理 |
| 中文分块效果差 | 检索不准 | 预留分块策略接口，可替换实现 |
| LLM 不调用 Tool | 功能不可用 | 优化 System Prompt，增加示例 |
| PgVector 检索慢 | 响应慢 | 调整 HNSW 参数，增加索引 |
| token 超限 | 对话中断 | 限制历史消息数，压缩上下文 |

---

## 九、后续扩展方向

完成导学 Agent 后，可逐步扩展：

1. **行情查询 Tool** — 接入股票/基金行情 API
2. **计算工具** — 收益率计算、风险评估
3. **个性化推荐** — 基于用户学习进度推荐内容
4. **多模态支持** — 图表识别、财报图片分析
5. **知识图谱** — 金融概念关联关系可视化

---

*文档生成时间：2026-06-17*
*项目仓库：my-claude-code*
