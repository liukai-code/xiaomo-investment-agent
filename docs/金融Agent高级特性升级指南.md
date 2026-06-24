# 金融投资 Agent 高级特性升级指南

> **项目名称：** 金融投资导学 Agent - 进阶升级
> **基准版本：** 基础版计划书 (Phase 0-6)
> **创建日期：** 2026-06-17
> **版本：** v1.0

---

## 一、升级总览

### 1.1 架构演进路线

```
Level 1: 基础 RAG                    ← 基础版已完成
  "用户问 → 向量检索 → 回答"

Level 2: Advanced RAG                ← 第一次升级
  "查询改写 → 多路检索 → 重排序 → 生成 → 引用"

Level 3: Agentic RAG                 ← 第二次升级
  "Agent 规划 → 多步检索 → 自我反思 → 迭代优化"

Level 4: Multi-Agent System          ← 最终形态
  "规划Agent + 检索Agent + 推理Agent + 验证Agent 协作"
```

### 1.2 升级阶段规划

| 阶段 | 目标 | 核心特性 | 预估工期 |
|------|------|----------|----------|
| 基础版 | 可用的 RAG 问答 | 向量检索 + Tool 调用 | 2周 (已完成计划) |
| 进阶版 V1 | 检索质量大幅提升 | 查询改写 + 混合检索 + Reranker | 1-2周 |
| 进阶版 V2 | Agent 智能化 | ReAct 推理 + Self-RAG | 2-3周 |
| 进阶版 V3 | 专业级金融 Agent | 知识图谱 + Multi-Agent | 3-4周 |

---

## 二、进阶版 V1：检索质量提升

### 2.1 查询改写 (Query Rewriting)

#### 原理

用户原始问题往往不适合直接检索。通过 LLM 改写查询，可以显著提升召回率。

```
原始问题: "ETF适合定投吗"
     ↓ LLM 改写
改写查询:
  1. "ETF定投策略的优缺点和历史收益"
  2. "指数基金定期投资的风险收益分析"
  3. "定投ETF与一次性投资的对比"
```

#### 实现方案

```java
/**
 * 查询改写器
 * 将用户原始问题改写为多个适合检索的查询
 */
@Service
public class QueryRewriter {

    private final ChatModel chatModel;

    private static final String REWRITE_PROMPT = """
        你是金融领域的查询改写专家。请将用户的原始问题改写为3个不同的检索查询，
        使这些查询能够更好地从金融知识库中检索到相关内容。

        要求：
        1. 保持原始问题的核心意图
        2. 使用更专业、更完整的金融术语
        3. 从不同角度描述同一问题
        4. 每个查询独立成句，用换行分隔

        原始问题：{query}

        改写查询（每行一个）：
        """;

    /**
     * 将一个原始问题改写为多个检索查询
     */
    public List<String> rewrite(String originalQuery) {
        String prompt = REWRITE_PROMPT.replace("{query}", originalQuery);
        String response = chatModel.call(prompt);

        return Arrays.stream(response.split("\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
```

#### 新增依赖

```xml
<!-- 无额外依赖，使用现有 Spring AI ChatModel -->
```

---

### 2.2 HyDE (Hypothetical Document Embeddings)

#### 原理

先让 LLM 生成一个"假想的理想答案"，再用这个答案去做向量检索。因为答案和知识库文档的语义更接近，检索效果比用问题检索更好。

```
用户问题: "什么是ETF"
     ↓ LLM 生成假想答案
假想答案: "ETF全称是交易型开放式指数基金，是一种在交易所上市交易的基金，
          跟踪特定指数的表现，具有费用低、透明度高、交易灵活等特点..."
     ↓ 用假想答案做向量检索
检索结果: (比直接用问题检索更精准)
```

#### 实现方案

```java
/**
 * HyDE 检索器
 * 先生成假设文档，再用假设文档做向量检索
 */
@Service
public class HyDERetriever {

    private final ChatModel chatModel;
    private final VectorStoreService vectorStore;

    private static final String HYPOTHESIS_PROMPT = """
        请根据以下金融问题，生成一段可能出现在金融教材中的标准答案。
        这个答案不需要完全准确，只需要包含相关的专业术语和概念。
        答案长度约150-200字。

        问题：{query}

        假想答案：
        """;

    /**
     * HyDE 检索流程
     */
    public List<RetrievalResult> retrieveWithHyDE(String query, int topK) {
        // Step 1: 生成假设文档
        String hypothesis = chatModel.call(
            HYPOTHESIS_PROMPT.replace("{query}", query)
        );

        // Step 2: 用假设文档做向量检索
        // 假设文档与知识库文档语义更接近，检索更准
        return vectorStore.search(hypothesis, topK);
    }
}
```

---

### 2.3 混合检索 (Hybrid Search)

#### 原理

单一向量检索有局限性。混合检索同时使用**语义检索（向量）**和**关键词检索（BM25）**，取长补短。

```
向量检索擅长：                              BM25 擅长：
  "什么是指数基金" → 找到ETF相关内容           "ETF费率" → 精确匹配关键词
  语义相近但措辞不同的内容                      专业术语、缩写、数字的精确匹配

混合检索 = 两者优势叠加
```

#### 实现方案

```java
/**
 * 混合检索器
 * 结合向量检索（语义）和 BM25 检索（关键词）
 */
@Service
public class HybridRetriever {

    private final VectorStore vectorStore;      // PgVector
    private final BM25Retriever bm25Retriever;  // 新增

    /**
     * 混合检索：向量 + BM25
     */
    public List<RetrievalResult> hybridSearch(String query, int topK) {
        // 并行执行两种检索
        CompletableFuture<List<RetrievalResult>> vectorFuture =
            CompletableFuture.supplyAsync(() -> vectorSearch(query, topK * 2));

        CompletableFuture<List<RetrievalResult>> bm25Future =
            CompletableFuture.supplyAsync(() -> bm25Search(query, topK * 2));

        List<RetrievalResult> vectorResults = vectorFuture.join();
        List<RetrievalResult> bm25Results = bm25Future.join();

        // Reciprocal Rank Fusion (RRF) 合并排序
        return reciprocalRankFusion(vectorResults, bm25Results, topK);
    }

    /**
     * RRF 融合算法
     * 基于排名的融合，不依赖分数，更稳定
     */
    private List<RetrievalResult> reciprocalRankFusion(
            List<RetrievalResult> list1,
            List<RetrievalResult> list2,
            int topK) {

        Map<String, Double> scoreMap = new HashMap<>();
        int k = 60;  // RRF 常数

        // 计算 list1 的 RRF 分数
        for (int i = 0; i < list1.size(); i++) {
            String id = list1.get(i).getId();
            scoreMap.merge(id, 1.0 / (k + i + 1), Double::sum);
        }

        // 计算 list2 的 RRF 分数
        for (int i = 0; i < list2.size(); i++) {
            String id = list2.get(i).getId();
            scoreMap.merge(id, 1.0 / (k + i + 1), Double::sum);
        }

        // 按融合分数排序，取 TopK
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> findResult(list1, list2, entry.getKey()))
            .collect(Collectors.toList());
    }
}
```

#### BM25 检索器实现

```java
/**
 * BM25 关键词检索器
 * 基于 PostgreSQL 全文检索实现
 */
@Service
public class BM25Retriever {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * BM25 检索（使用 PostgreSQL ts_rank）
     */
    public List<RetrievalResult> search(String query, int topK) {
        // 将查询分词（中文需要 pg_jieba 扩展或应用层分词）
        String tsQuery = buildTsQuery(query);

        String sql = """
            SELECT id, content, metadata,
                   ts_rank(search_vector, to_tsquery('chinese', ?)) AS rank
            FROM vector_store
            WHERE search_vector @@ to_tsquery('chinese', ?)
            ORDER BY rank DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, new Object[]{tsQuery, tsQuery, topK},
            (rs, rowNum) -> new RetrievalResult(
                rs.getString("id"),
                rs.getString("content"),
                rs.getDouble("rank"),
                parseMetadata(rs.getString("metadata"))
            ));
    }

    /**
     * 构建 PostgreSQL 全文查询
     */
    private String buildTsQuery(String query) {
        // 简单分词：按空格/标点分割，用 & 连接
        return Arrays.stream(query.split("[\\s,，。、]+"))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(" & "));
    }
}
```

#### 数据库变更

```sql
-- 为 vector_store 表添加全文检索支持
ALTER TABLE vector_store ADD COLUMN search_vector tsvector;

-- 创建 GIN 索引
CREATE INDEX idx_vector_store_search ON vector_store USING gin(search_vector);

-- 创建触发器自动更新 search_vector
CREATE OR REPLACE FUNCTION update_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('chinese', COALESCE(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_search_vector
    BEFORE INSERT OR UPDATE ON vector_store
    FOR EACH ROW EXECUTE FUNCTION update_search_vector();
```

#### 新增依赖

```xml
<!-- PostgreSQL 中文分词 (可选，效果更好) -->
<dependency>
    <groupId>com.hankcs</groupId>
    <artifactId>hanlp</artifactId>
    <version>portable-1.8.4</version>
</dependency>
```

---

### 2.4 Reranker 重排序

#### 原理

向量检索是"双塔模型"，query 和 document 分别编码，速度快但精度有限。
Reranker 是"交叉编码器"，query 和 document 一起编码，速度慢但精度高。

策略：**先用向量检索粗筛 Top 20，再用 Reranker 精排取 Top 5。**

```
向量检索 (粗筛):           Reranker (精排):
  速度快 (毫秒级)            速度慢 (百毫秒级)
  精度一般                   精度高
  适合大数据量筛选            适合少量数据精排

组合: 向量检索 Top20 → Reranker → Top5
```

#### 实现方案

```java
/**
 * Reranker 重排序服务
 * 使用阿里通义的 Reranker API 或本地 BGE-Reranker
 */
@Service
public class RerankerService {

    private final RestTemplate restTemplate;

    // 阿里通义 Reranker API
    private static final String RERANK_API_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/reranker/text-reranking/text-reranking";

    @Value("${ali.api-key}")
    private String apiKey;

    /**
     * 对检索结果进行重排序
     *
     * @param query 用户查询
     * @param documents 候选文档列表
     * @param topK 返回 Top K 个结果
     * @return 重排序后的结果
     */
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = Map.of(
            "model", "gte-rerank",   // 阿里通义 Reranker 模型
            "input", Map.of(
                "query", query,
                "documents", documents
            ),
            "parameters", Map.of(
                "top_n", topK,
                "return_documents", true
            )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<RerankResponse> response = restTemplate.exchange(
            RERANK_API_URL,
            HttpMethod.POST,
            request,
            RerankResponse.class
        );

        return response.getBody().getResults();
    }
}

/**
 * Reranker 响应结构
 */
@Data
public class RerankResponse {
    private List<RerankResult> results;

    @Data
    public static class RerankResult {
        private int index;
        private double relevanceScore;
        private String document;
    }
}
```

#### 集成到检索流程

```java
/**
 * 增强版检索器：混合检索 + Reranker
 */
@Service
public class EnhancedRetriever {

    private final HybridRetriever hybridRetriever;
    private final RerankerService rerankerService;

    private static final int CANDIDATE_SIZE = 20;  // 粗筛数量
    private static final int FINAL_SIZE = 5;       // 最终返回数量

    public List<RetrievalResult> retrieve(String query) {
        // Step 1: 混合检索粗筛 Top 20
        List<RetrievalResult> candidates = hybridRetriever.hybridSearch(
            query, CANDIDATE_SIZE
        );

        // Step 2: Reranker 精排取 Top 5
        List<String> documents = candidates.stream()
            .map(RetrievalResult::getContent)
            .collect(Collectors.toList());

        List<RerankerService.RerankResult> reranked =
            rerankerService.rerank(query, documents, FINAL_SIZE);

        // Step 3: 映射回原始结果（带元数据）
        return reranked.stream()
            .map(r -> candidates.get(r.getIndex()))
            .collect(Collectors.toList());
    }
}
```

---

### 2.5 查询处理器 (Query Processor)

将查询改写、HyDE、意图识别整合为统一的查询处理管道。

```java
/**
 * 查询处理器
 * 对用户原始查询进行预处理，输出适合检索的查询
 */
@Service
public class QueryProcessor {

    private final QueryRewriter queryRewriter;
    private final ChatModel chatModel;

    private static final String INTENT_PROMPT = """
        分析以下金融问题，输出JSON格式的意图分析：

        问题：{query}

        输出格式：
        {
            "intent": "concept|strategy|calculation|realtime|chitchat",
            "needRetrieval": true/false,
            "category": "教材|法规|研报|",
            "keywords": ["关键词1", "关键词2"]
        }
        """;

    /**
     * 处理用户查询，返回结构化查询信息
     */
    public ProcessedQuery process(String originalQuery) {
        // Step 1: 意图识别
        IntentAnalysis intent = analyzeIntent(originalQuery);

        // Step 2: 判断是否需要检索
        if (!intent.isNeedRetrieval()) {
            return ProcessedQuery.noRetrieval(originalQuery, intent);
        }

        // Step 3: 查询改写
        List<String> rewrittenQueries = queryRewriter.rewrite(originalQuery);

        return ProcessedQuery.withRetrieval(
            originalQuery,
            rewrittenQueries,
            intent
        );
    }

    private IntentAnalysis analyzeIntent(String query) {
        String prompt = INTENT_PROMPT.replace("{query}", query);
        String response = chatModel.call(prompt);
        return JsonUtil.parse(response, IntentAnalysis.class);
    }
}

@Data
public class ProcessedQuery {
    private String originalQuery;
    private List<String> retrievalQueries;
    private IntentAnalysis intent;
    private boolean needRetrieval;

    // 工厂方法
    public static ProcessedQuery noRetrieval(String query, IntentAnalysis intent) { ... }
    public static ProcessedQuery withRetrieval(String query, List<String> rewrites, IntentAnalysis intent) { ... }
}

@Data
public class IntentAnalysis {
    private String intent;        // concept/strategy/calculation/realtime/chitchat
    private boolean needRetrieval;
    private String category;
    private List<String> keywords;
}
```

---

## 三、进阶版 V2：Agent 智能化

### 3.1 ReAct 推理模式

#### 原理

ReAct = Reasoning + Acting。Agent 不是一次调用就结束，而是**思考 → 行动 → 观察 → 再思考**的循环。

```
传统模式:                        ReAct 模式:
  用户提问                         用户提问
     ↓                              ↓
  检索一次                         Thought: 需要先查ETF定义
     ↓                              ↓
  生成回答                         Action: 检索("ETF定义")
                                     ↓
                                   Observation: ETF是交易型开放式指数基金...
                                     ↓
                                   Thought: 还需要了解定投策略
                                     ↓
                                   Action: 检索("定投策略优缺点")
                                     ↓
                                   Observation: 定投可以平滑成本...
                                     ↓
                                   Thought: 信息足够，可以回答了
                                     ↓
                                   Final Answer: 综合回答
```

#### 实现方案

```java
/**
 * ReAct Agent
 * 实现 Thought → Action → Observation 循环
 */
@Service
public class ReActAgent {

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;

    private static final int MAX_ITERATIONS = 5;  // 最大循环次数

    private static final String REACT_PROMPT = """
        你是金融投资导学助手。请使用以下格式回答问题：

        Thought: 分析当前情况，决定下一步行动
        Action: 调用工具（格式：工具名称[参数]）
        Observation: 工具返回的结果
        ... (可重复 Thought/Action/Observation)
        Thought: 我现在可以给出最终答案了
        Final Answer: 最终回答

        可用工具：
        {tools}

        问题：{question}

        开始推理：
        """;

    /**
     * ReAct 推理循环
     */
    public String react(String question) {
        List<String> toolDescriptions = toolRegistry.getDescriptions();
        String prompt = REACT_PROMPT
            .replace("{tools}", String.join("\n", toolDescriptions))
            .replace("{question}", question);

        StringBuilder context = new StringBuilder();
        context.append(prompt);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // LLM 生成下一步 Thought/Action
            String response = chatModel.call(context.toString());
            context.append(response);

            // 解析 Action
            Action parsed = parseAction(response);

            if (parsed == null) {
                // 没有 Action，说明已经是 Final Answer
                return extractFinalAnswer(response);
            }

            // 执行 Action，获取 Observation
            String observation = toolRegistry.execute(
                parsed.toolName(),
                parsed.parameters()
            );

            context.append("\nObservation: ").append(observation).append("\n");
        }

        // 达到最大循环次数，强制生成回答
        context.append("\nThought: 已收集足够信息，现在给出最终答案。\nFinal Answer: ");
        return chatModel.call(context.toString());
    }

    private record Action(String toolName, String parameters) {}

    private Action parseAction(String response) {
        // 解析 "Action: toolName[parameters]" 格式
        Pattern pattern = Pattern.compile("Action:\\s*(\\w+)\\[(.+?)\\]");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return new Action(matcher.group(1), matcher.group(2));
        }
        return null;
    }
}
```

---

### 3.2 Plan-and-Execute 模式

#### 原理

对于复杂问题，先制定完整计划，再逐步执行。比 ReAct 更适合多步推理。

```
用户问题: "对比ETF和主动基金的风险收益，给出定投建议"

Plan:
  1. 检索ETF的特点和历史收益数据
  2. 检索主动管理基金的特点和历史收益
  3. 检索定投策略的适用条件
  4. 对比分析，给出建议

Execute:
  Step 1 → 检索ETF相关 → 得到结果A
  Step 2 → 检索主动基金 → 得到结果B
  Step 3 → 检索定投策略 → 得到结果C
  Step 4 → 综合A+B+C → 生成最终回答
```

#### 实现方案

```java
/**
 * Plan-and-Execute Agent
 * 先规划再执行，适合复杂多步问题
 */
@Service
public class PlanAndExecuteAgent {

    private final ChatModel chatModel;
    private final EnhancedRetriever retriever;

    private static final String PLANNING_PROMPT = """
        你是金融投资导学助手的规划模块。请将以下复杂问题分解为可执行的步骤。

        问题：{question}

        输出JSON格式的计划：
        {
            "steps": [
                {"step": 1, "action": "检索", "query": "具体检索查询"},
                {"step": 2, "action": "检索", "query": "具体检索查询"},
                {"step": 3, "action": "分析", "description": "对比分析步骤1和2的结果"},
                {"step": 4, "action": "回答", "description": "生成最终回答"}
            ]
        }
        """;

    private static final String EXECUTE_PROMPT = """
        基于以下检索结果，回答用户问题。

        用户问题：{question}

        检索结果：
        {context}

        请给出专业、准确、有引用来源的回答。
        """;

    /**
     * 执行 Plan-and-Execute 流程
     */
    public String execute(String question) {
        // Step 1: 制定计划
        String planJson = chatModel.call(
            PLANNING_PROMPT.replace("{question}", question)
        );
        Plan plan = JsonUtil.parse(planJson, Plan.class);

        // Step 2: 逐步执行
        StringBuilder context = new StringBuilder();
        for (PlanStep step : plan.getSteps()) {
            if ("检索".equals(step.getAction())) {
                List<RetrievalResult> results = retriever.retrieve(step.getQuery());
                context.append("【").append(step.getQuery()).append("】\n");
                for (RetrievalResult r : results) {
                    context.append("- ").append(r.getContent())
                           .append(" (来源: ").append(r.getSource()).append(")\n");
                }
                context.append("\n");
            }
        }

        // Step 3: 综合生成回答
        String finalPrompt = EXECUTE_PROMPT
            .replace("{question}", question)
            .replace("{context}", context.toString());

        return chatModel.call(finalPrompt);
    }
}
```

---

### 3.3 Self-RAG (自我反思)

#### 原理

Agent 生成回答后，自我检查回答质量，发现问题则重新检索或修正。

```
生成回答
   ↓
反思检查:
  ├─ 检查1: 回答是否需要检索支持？
  │   → 需要但没检索 → 重新检索
  │
  ├─ 检查2: 检索结果是否相关？
  │   → 不相关 → 改写查询，重新检索
  │
  ├─ 检查3: 回答是否忠于检索内容？
  │   → 有幻觉 → 修正回答
  │
  └─ 检查4: 回答是否完整？
      → 不完整 → 补充检索
   ↓
最终回答（带置信度标记）
```

#### 实现方案

```java
/**
 * Self-RAG 自我反思服务
 */
@Service
public class SelfRAGService {

    private final ChatModel chatModel;
    private final EnhancedRetriever retriever;

    private static final String REFLECTION_PROMPT = """
        请评估以下回答的质量，输出JSON格式的评估结果：

        用户问题：{question}
        检索内容：{context}
        当前回答：{answer}

        评估维度：
        1. needRetrieval: 回答是否需要检索支持（true/false）
        2. retrievalRelevant: 检索结果是否与问题相关（1-5分）
        3. faithfulness: 回答是否忠于检索内容（1-5分）
        4. completeness: 回答是否完整（1-5分）
        5. hallucination: 是否存在幻觉（true/false）
        6. suggestion: 改进建议

        输出JSON：
        {
            "needRetrieval": true,
            "retrievalRelevant": 4,
            "faithfulness": 5,
            "completeness": 3,
            "hallucination": false,
            "suggestion": "建议补充ETF的具体费用说明"
        }
        """;

    /**
     * 带自我反思的 RAG 流程
     */
    public String retrieveWithReflection(String question) {
        // 第一轮：检索 + 生成
        List<RetrievalResult> results = retriever.retrieve(question);
        String context = formatContext(results);
        String answer = generateAnswer(question, context);

        // 自我反思
        ReflectionResult reflection = reflect(question, context, answer);

        // 根据反思结果决定是否需要修正
        if (reflection.getFaithfulness() < 3 || reflection.isHallucination()) {
            // 忠实度低或有幻觉，重新检索
            String refinedQuery = refineQuery(question, reflection.getSuggestion());
            List<RetrievalResult> newResults = retriever.retrieve(refinedQuery);
            String newContext = formatContext(newResults);
            answer = generateAnswer(question, newContext);
        }

        if (reflection.getCompleteness() < 3) {
            // 回答不完整，补充检索
            String补充Query = reflection.getSuggestion();
            List<RetrievalResult>补充Results = retriever.retrieve(补充Query);
            String补充Context = formatContext(补充Results);
            answer = generateAnswer(question, context + "\n" + 补充Context);
        }

        return answer;
    }

    private ReflectionResult reflect(String question, String context, String answer) {
        String prompt = REFLECTION_PROMPT
            .replace("{question}", question)
            .replace("{context}", context)
            .replace("{answer}", answer);

        String response = chatModel.call(prompt);
        return JsonUtil.parse(response, ReflectionResult.class);
    }
}
```

---

## 四、进阶版 V3：专业级金融 Agent

### 4.1 知识图谱增强

#### 原理

金融领域概念之间有丰富的关联关系，纯向量检索无法捕获这些结构化知识。
知识图谱可以回答"A和B有什么关系"这类问题。

```
金融知识图谱示例：

  ETF ──是一种──→ 基金
  ETF ──跟踪──→ 指数
  ETF ──特点──→ 低费率
  ETF ──特点──→ 透明度高
  定投 ──适用于──→ ETF
  定投 ──降低──→ 择时风险
  定投 ──适合──→ 长期投资
  巴菲特 ──推荐──→ 指数投资
  市盈率 ──用于──→ 股票估值
  市盈率 ──计算公式──→ 股价/每股收益
```

#### 实现方案

```java
/**
 * 知识图谱服务
 * 使用 Neo4j 存储金融概念关系
 */
@Service
public class KnowledgeGraphService {

    @Autowired
    private Neo4jClient neo4jClient;

    /**
     * 从文档中提取实体和关系
     */
    public void extractAndStore(String documentId, String content) {
        // 使用 LLM 提取实体和关系
        String extraction = chatModel.call(EXTRACTION_PROMPT.replace("{text}", content));
        EntityRelationList entities = JsonUtil.parse(extraction, EntityRelationList.class);

        // 存入 Neo4j
        for (EntityRelation er : entities) {
            neo4jClient.run(
                "MERGE (a:Concept {name: $from}) " +
                "MERGE (b:Concept {name: $to}) " +
                "MERGE (a)-[r:RELATION {type: $rel}]->(b)",
                Map.of("from", er.getFrom(), "to", er.getTo(), "rel", er.getRelation())
            );
        }
    }

    /**
     * 查询概念关联
     */
    public List<ConceptPath> queryRelations(String concept) {
        String cypher = """
            MATCH path = (start:Concept {name: $concept})-[*1..3]-(related:Concept)
            RETURN path
            LIMIT 20
            """;

        return neo4jClient.run(cypher)
            .bind(concept).to("concept")
            .fetchAs(ConceptPath.class)
            .all();
    }

    /**
     * 混合检索：向量 + 知识图谱
     */
    public List<RetrievalResult> hybridRetrieve(String query) {
        // 向量检索
        List<RetrievalResult> vectorResults = vectorRetriever.retrieve(query, 10);

        // 从查询中提取概念
        List<String> concepts = extractConcepts(query);

        // 知识图谱查询
        List<ConceptPath> graphResults = concepts.stream()
            .flatMap(c -> queryRelations(c).stream())
            .collect(Collectors.toList());

        // 融合结果
        return mergeResults(vectorResults, graphResults);
    }
}
```

#### 数据库变更

```yaml
# Neo4j 配置 (新增)
spring:
  neo4j:
    uri: bolt://localhost:7687
    username: neo4j
    password: your_password
```

```xml
<!-- 新增依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>
```

---

### 4.2 Multi-Agent 协作

#### 原理

多个专业 Agent 各司其职，协同完成复杂任务。

```
用户问题: "我有10万本金，想做基金定投，请给一个配置方案"

                    ┌─────────────────┐
                    │  Planning Agent  │
                    │  (规划者)        │
                    └────────┬────────┘
                             │ 制定计划
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
   │ Knowledge    │  │ Analysis     │  │ Risk        │
   │ Agent        │  │ Agent        │  │ Agent       │
   │ (知识检索)    │  │ (数据分析)    │  │ (风险评估)   │
   │              │  │              │  │             │
   │ 检索定投策略  │  │ 查询历史收益  │  │ 评估风险等级  │
   │ 检索基金类型  │  │ 计算回撤     │  │ 计算波动率   │
   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
          └────────────────┼────────────────┘
                           ▼
                  ┌─────────────────┐
                  │ Synthesis Agent  │
                  │ (综合者)         │
                  │                  │
                  │ 整合所有结果      │
                  │ 生成配置方案      │
                  │ 附加风险提示      │
                  └────────┬────────┘
                           ▼
                      最终回答
```

#### 实现方案

```java
/**
 * Multi-Agent 协调器
 */
@Service
public class MultiAgentOrchestrator {

    private final PlanningAgent planningAgent;
    private final KnowledgeAgent knowledgeAgent;
    private final AnalysisAgent analysisAgent;
    private final RiskAgent riskAgent;
    private final SynthesisAgent synthesisAgent;

    /**
     * 协调多个 Agent 完成复杂任务
     */
    public String orchestrate(String question) {
        // Step 1: Planning Agent 制定计划
        Plan plan = planningAgent.createPlan(question);

        // Step 2: 并行执行各专业 Agent
        Map<String, String> results = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = plan.getSubTasks().stream()
            .map(task -> CompletableFuture.runAsync(() -> {
                String result = switch (task.getAgentType()) {
                    case "knowledge" -> knowledgeAgent.execute(task);
                    case "analysis" -> analysisAgent.execute(task);
                    case "risk" -> riskAgent.execute(task);
                    default -> "";
                };
                results.put(task.getId(), result);
            }))
            .collect(Collectors.toList());

        // 等待所有 Agent 完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Step 3: Synthesis Agent 综合结果
        return synthesisAgent.synthesize(question, results);
    }
}

/**
 * 知识检索 Agent
 */
@Service
public class KnowledgeAgent {

    private final EnhancedRetriever retriever;

    public String execute(SubTask task) {
        // 专注于知识检索
        List<RetrievalResult> results = retriever.retrieve(task.getQuery());
        return formatResults(results);
    }
}

/**
 * 数据分析 Agent
 */
@Service
public class AnalysisAgent {

    private final MarketDataService marketData;

    public String execute(SubTask task) {
        // 专注于数据分析
        // 接入行情API、数据库等
        return performAnalysis(task);
    }
}

/**
 * 风险评估 Agent
 */
@Service
public class RiskAgent {

    public String execute(SubTask task) {
        // 专注于风险评估
        return assessRisk(task);
    }
}
```

---

### 4.3 金融领域评估体系

#### RAGAS 评估框架

```java
/**
 * RAGAS 评估服务
 * 自动评估 Agent 回答质量
 */
@Service
public class RagasEvaluationService {

    private final ChatModel chatModel;

    /**
     * 评估指标
     */
    public EvalResult evaluate(String question, String answer,
                                List<String> contexts, String groundTruth) {
        EvalResult result = new EvalResult();

        // 1. Faithfulness (忠实度)
        // 回答是否忠于检索到的上下文
        result.setFaithfulness(evalFaithfulness(answer, contexts));

        // 2. Answer Relevancy (回答相关性)
        // 回答是否与问题相关
        result.setAnswerRelevancy(evalAnswerRelevancy(question, answer));

        // 3. Context Precision (上下文精度)
        // 检索到的内容是否与问题相关
        result.setContextPrecision(evalContextPrecision(question, contexts));

        // 4. Context Recall (上下文召回)
        // 是否检索到了所有相关内容
        result.setContextRecall(evalContextRecall(answer, contexts, groundTruth));

        // 5. Hallucination Rate (幻觉率)
        result.setHallucinationRate(evalHallucination(answer, contexts));

        return result;
    }

    private double evalFaithfulness(String answer, List<String> contexts) {
        String prompt = """
            评估以下回答是否忠于给定的上下文内容。

            回答：{answer}

            上下文：
            {contexts}

            请给出 0-1 的忠实度分数，并简要说明。
            JSON格式：{"score": 0.95, "reason": "..."}
            """;

        String response = chatModel.call(prompt
            .replace("{answer}", answer)
            .replace("{contexts}", String.join("\n", contexts)));

        return JsonUtil.parse(response, ScoreResult.class).getScore();
    }
}
```

---

## 五、配置与依赖汇总

### 5.1 完整 Maven 依赖

```xml
<!-- 基础版依赖 (已有) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>

<!-- 进阶版 V1 依赖 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>com.hankcs</groupId>
    <artifactId>hanlp</artifactId>
    <version>portable-1.8.4</version>
</dependency>

<!-- 进阶版 V3 依赖 (知识图谱) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>

<!-- 工具库 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

### 5.2 完整 application.yml

```yaml
server:
  port: 4545

spring:
  # PostgreSQL
  datasource:
    url: jdbc:postgresql://localhost:5432/financial_agent
    username: postgres
    password: ${DB_PASSWORD}

  # Neo4j (进阶版 V3)
  neo4j:
    uri: bolt://localhost:7687
    username: neo4j
    password: ${NEO4J_PASSWORD}

  # JPA
  jpa:
    hibernate:
      ddl-auto: update

  # Spring AI
  ai:
    # Anthropic (主 LLM)
    anthropic:
      base-url: https://token-plan-cn.xiaomimimo.com/anthropic
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: mimo-v2.5-pro

    # 阿里通义 Embedding (OpenAI 兼容接口)
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${ALI_API_KEY}
      embedding:
        options:
          model: text-embedding-v3

    # PgVector
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1024
        table-name: vector_store

# 自定义配置
financial-agent:
  rag:
    candidate-size: 20
    final-size: 5
    similarity-threshold: 0.7
  session:
    timeout: 30m
    max-history: 20
```

---

## 六、升级检查清单

### 进阶版 V1 检查项

- [ ] QueryRewriter 实现并集成
- [ ] HyDERetriever 实现并集成
- [ ] BM25Retriever 实现（需 PostgreSQL 全文检索配置）
- [ ] HybridRetriever 实现（RRF 融合算法）
- [ ] RerankerService 接入阿里 Reranker API
- [ ] EnhancedRetriever 集成混合检索 + Reranker
- [ ] QueryProcessor 实现（意图识别 + 查询处理）
- [ ] 检索效果对比测试（基础版 vs 进阶版）

### 进阶版 V2 检查项

- [ ] ReActAgent 实现
- [ ] PlanAndExecuteAgent 实现
- [ ] SelfRAGService 实现
- [ ] Agent 推理模式可配置（单次/ReAct/Plan-Execute）
- [ ] 推理循环次数限制和超时处理
- [ ] 复杂问题测试用例

### 进阶版 V3 检查项

- [ ] Neo4j 部署和配置
- [ ] KnowledgeGraphService 实现
- [ ] 实体关系提取 Prompt 优化
- [ ] MultiAgentOrchestrator 实现
- [ ] 各专业 Agent 实现（Knowledge/Analysis/Risk）
- [ ] Agent 间通信和结果融合
- [ ] RagasEvaluationService 实现
- [ ] 评估指标基线测试

---

## 七、性能与成本预估

### 7.1 Token 消耗预估

| 场景 | 基础版 | 进阶版 V1 | 进阶版 V2 | 进阶版 V3 |
|------|--------|-----------|-----------|-----------|
| 简单问答 | ~2K | ~3K | ~4K | ~5K |
| 中等复杂 | ~3K | ~5K | ~8K | ~12K |
| 复杂分析 | ~5K | ~8K | ~15K | ~25K |

### 7.2 响应时间预估

| 场景 | 基础版 | 进阶版 V1 | 进阶版 V2 | 进阶版 V3 |
|------|--------|-----------|-----------|-----------|
| 简单问答 | 1-2s | 2-3s | 3-5s | 5-8s |
| 中等复杂 | 2-3s | 3-5s | 5-10s | 8-15s |
| 复杂分析 | 3-5s | 5-8s | 10-20s | 15-30s |

### 7.3 成本优化建议

1. **缓存热点查询** — 高频问题缓存回答，减少 LLM 调用
2. **Embedding 缓存** — 相同文档不重复向量化
3. **分级策略** — 简单问题用基础版流程，复杂问题才走高级流程
4. **模型选择** — 查询改写用小模型，最终回答用大模型

---

*文档生成时间：2026-06-17*
*关联文档：金融投资导学Agent开发计划书.md*
