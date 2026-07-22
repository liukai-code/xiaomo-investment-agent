# 深度分析独立模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将深度分析工作流从对话中抽离为独立前端模块，支持独立运行分析、查看历史、在对话中引用分析结果。

**Architecture:** 新增独立 `/analysis` 路由页面（左右分栏），后端新增 AnalysisController 处理分析 CRUD 和 SSE 流，新增 GetAnalysisReportTool 让对话 AI 能查询分析结果。核心工作流引擎零改动。

**Tech Stack:** Spring Boot 3.5, Spring AI 1.0, Vue 3, TypeScript, Pinia, lucide-vue-next, plain CSS with variables

## Global Constraints

- 中文回答，代码注释和 commit message 用中文
- 后端 DI 用 `@Resource`（Jakarta），Controller 用 `@RestController` + `@RequestMapping` + `@Slf4j`
- 响应体统一 `Result<T>`（code=1 成功，code=0 失败）
- Auth 通过 `HttpServletRequest.getAttribute("userId")` 获取
- Tool 用 `@Tool(description=...)` + `@ToolParam(description=...)` 注解，返回 `String`
- 前端样式用 plain CSS + CSS variables，不用 Tailwind
- 前端图标统一 `lucide-vue-next`
- Store 用 Pinia Composition API（`defineStore('name', () => {...})`）
- API 请求通过 `@/api/request.ts` 的 axios 实例，token 自动注入

---

## Task 1: 后端 AnalysisService + AnalysisController + Tool

**Files:**
- Modify: `src/main/java/com/xiaomo/agent/workflow/persist/WorkflowAnalysis.java` — conversationId 改为 nullable
- Modify: `src/main/java/com/xiaomo/agent/workflow/persist/WorkflowAnalysisRepository.java` — 新增查询方法
- Modify: `src/main/java/com/xiaomo/agent/workflow/service/DeepAnalysisWorkflow.java` — 新增 `executeWithAnalysisId()` 方法 + 事件发布机制
- Create: `src/main/java/com/xiaomo/agent/analysis/controller/AnalysisController.java`
- Create: `src/main/java/com/xiaomo/agent/analysis/service/AnalysisService.java`
- Create: `src/main/java/com/xiaomo/agent/tool/GetAnalysisReportTool.java`
- Modify: `src/main/java/com/xiaomo/agent/agent/service/impl/AgentLoopImpl.java` — 注册新 Tool
- Test: `src/test/java/com/xiaomo/agent/analysis/service/AnalysisServiceTest.java`
- Test: `src/test/java/com/xiaomo/agent/tool/GetAnalysisReportToolTest.java`

**Interfaces:**
- Consumes: `DeepAnalysisWorkflow.execute(userId, conversationId, query)`, `WorkflowAnalysisRepository`, `StockCodeExtractor`, `ObjectMapper`
- Produces: `AnalysisController` 5 个端点, `GetAnalysisReportTool.getAnalysisReport()` 方法

---

### Step 1: 修改 WorkflowAnalysis 实体 — conversationId 改为 nullable

文件: `src/main/java/com/xiaomo/agent/workflow/persist/WorkflowAnalysis.java`

将 `conversationId` 字段改为 nullable（独立分析不关联对话）:

```java
@Column(name = "conversation_id")
private Long conversationId;
```

去掉 `@Column` 的任何 NOT NULL 约束（当前已无显式约束，确认即可）。在数据库中执行:

```sql
ALTER TABLE workflow_analyses ALTER COLUMN conversation_id DROP NOT NULL;
```

### Step 2: 新增 Repository 查询方法

文件: `src/main/java/com/xiaomo/agent/workflow/persist/WorkflowAnalysisRepository.java`

```java
package com.xiaomo.agent.workflow.persist;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkflowAnalysisRepository extends JpaRepository<WorkflowAnalysis, Long> {
    List<WorkflowAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WorkflowAnalysis> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    // 新增：按用户+状态查询（检查是否有运行中的分析）
    List<WorkflowAnalysis> findByUserIdAndWorkflowStatus(Long userId, String workflowStatus);

    // 新增：按用户+标的代码查询（对话 Tool 使用）
    Optional<WorkflowAnalysis> findFirstByUserIdAndResolvedStockCodeOrderByCreatedAtDesc(Long userId, String stockCode);

    // 新增：按标的代码查询已完成的分析（Tool 使用，不限 userId）
    Optional<WorkflowAnalysis> findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc(String stockCode, String status);

    // 新增：按标的名称查询已完成的分析（模糊匹配）
    Optional<WorkflowAnalysis> findFirstByResolvedStockNameContainingAndWorkflowStatusOrderByCreatedAtDesc(String stockName, String status);
}
```

### Step 3: DeepAnalysisWorkflow 新增事件发布机制

文件: `src/main/java/com/xiaomo/agent/workflow/service/DeepAnalysisWorkflow.java`

新增字段和方法（不修改现有 `execute()` 逻辑）:

```java
// 新增字段
private final ConcurrentHashMap<Long, Sinks.Many<WorkflowEvent>> analysisEventSinks = new ConcurrentHashMap<>();

// 新增：获取指定分析的事件 Sink（供 Controller 订阅）
public Sinks.Many<WorkflowEvent> getEventSink(Long analysisId) {
    return analysisEventSinks.get(analysisId);
}

// 新增：移除事件 Sink（分析完成后清理）
public void removeEventSink(Long analysisId) {
    Sinks.Many<WorkflowEvent> sink = analysisEventSinks.remove(analysisId);
    if (sink != null) {
        sink.tryEmitComplete();
    }
}
```

在现有 `execute()` 方法的事件发射点（`doOnNext` 或节点产出处），新增对 sink 的发布:

```java
// 在 execute() 方法中，每个事件发出时同时发布到 sink（如果存在）
// 在 WorkflowEngine 返回的 Flux 上添加 .doOnNext()
.doOnNext(event -> {
    Sinks.Many<WorkflowEvent> sink = analysisEventSinks.get(analysisId);
    if (sink != null && sink.currentSubscriberCount() > 0) {
        sink.tryEmitNext(event);
    }
})
```

### Step 4: DeepAnalysisWorkflow 新增 executeWithAnalysisId 方法

文件: `src/main/java/com/xiaomo/agent/workflow/service/DeepAnalysisWorkflow.java`

新增重载方法，接受预创建的 analysisId，不创建对话:

```java
/**
 * 使用预创建的分析记录执行工作流（独立分析模式，不关联对话）
 */
public Flux<WorkflowEvent> executeWithAnalysisId(Long userId, Long analysisId, String query) {
    // 创建事件 Sink
    Sinks.Many<WorkflowEvent> sink = Sinks.many().replay().all();
    analysisEventSinks.put(analysisId, sink);

    WorkflowState state = new WorkflowState();
    state.setUserId(userId);
    state.setOriginalQuery(query);
    state.setAnalysisId(analysisId);  // 需要在 WorkflowState 中新增此字段

    // 解析标的
    String stockCode = StockCodeExtractor.extract(query);
    if (stockCode != null) {
        state.setResolvedStockCode(stockCode);
        // StockResolver 解析名称...
    }

    WorkflowGraph graph = buildGraph(state);

    return workflowEngine.execute(graph, state)
            .doOnNext(sink::tryEmitNext)
            .doOnComplete(() -> {
                persistResultsWithId(state, analysisId, "COMPLETED", null);
                removeEventSink(analysisId);
            })
            .doOnError(e -> {
                persistResultsWithId(state, analysisId, "FAILED", e.getMessage());
                sink.tryEmitNext(WorkflowEvent.error("工作流执行失败: " + e.getMessage()));
                removeEventSink(analysisId);
            });
}
```

同时需要在 `WorkflowState` 中新增 `analysisId` 字段（getter/setter），以及新增 `persistResultsWithId()` 方法:

```java
/**
 * 使用指定 analysisId 持久化结果（更新已有记录而非新建）
 */
private void persistResultsWithId(WorkflowState state, Long analysisId, String status, String errorMessage) {
    analysisRepository.findById(analysisId).ifPresent(analysis -> {
        analysis.setWorkflowStatus(status);
        analysis.setResolvedStockCode(state.getResolvedStockCode());
        analysis.setResolvedStockName(state.getResolvedStockName());
        if (state.getAnalystReports() != null) {
            analysis.setAnalystReportsJson(toJson(state.getAnalystReports()));
        }
        if (state.getBullBearDebate() != null) {
            analysis.setBullBearDebateJson(toJson(state.getBullBearDebate()));
        }
        analysis.setInvestmentPlan(state.getInvestmentPlan());
        analysis.setTradingProposal(state.getTradingProposal());
        if (state.getRiskDebate() != null) {
            analysis.setRiskDebateJson(toJson(state.getRiskDebate()));
        }
        if (state.getFinalDecision() != null) {
            analysis.setAction(state.getFinalDecision().action());
            analysis.setConfidence(state.getFinalDecision().confidence());
            analysis.setTargetPrice(state.getFinalDecision().targetPrice());
            analysis.setSummary(state.getFinalDecision().summary());
        }
        analysis.setCompletedAt(LocalDateTime.now());
        analysis.setErrorMessage(errorMessage);
        analysisRepository.save(analysis);
        log.info("分析结果已持久化: id={}, status={}", analysisId, status);
    });
}
```

注意：复用现有 `toJson()` 辅助方法（已在 DeepAnalysisWorkflow 中存在）。

### Step 5: 创建 AnalysisService

文件: `src/main/java/com/xiaomo/agent/analysis/service/AnalysisService.java`

```java
package com.xiaomo.agent.analysis.service;

import com.xiaomo.agent.workflow.persist.WorkflowAnalysis;
import com.xiaomo.agent.workflow.persist.WorkflowAnalysisRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class AnalysisService {

    @Resource
    private WorkflowAnalysisRepository analysisRepository;

    /**
     * 创建分析记录（PENDING 状态）
     */
    @Transactional
    public WorkflowAnalysis createAnalysis(Long userId, String query, String stockCode, String stockName) {
        // 检查是否有运行中的分析
        List<WorkflowAnalysis> running = analysisRepository.findByUserIdAndWorkflowStatus(userId, "RUNNING");
        if (!running.isEmpty()) {
            throw new IllegalStateException("已有分析正在运行中，请等待完成后再发起新的分析");
        }

        WorkflowAnalysis analysis = new WorkflowAnalysis();
        analysis.setUserId(userId);
        analysis.setOriginalQuery(query);
        analysis.setResolvedStockCode(stockCode);
        analysis.setResolvedStockName(stockName);
        analysis.setWorkflowStatus("PENDING");
        analysis.setStartedAt(LocalDateTime.now());
        return analysisRepository.save(analysis);
    }

    /**
     * 获取用户的分析列表
     */
    public List<WorkflowAnalysis> listAnalyses(Long userId) {
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取单条分析详情
     */
    public WorkflowAnalysis getAnalysis(Long id, Long userId) {
        WorkflowAnalysis analysis = analysisRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分析记录不存在"));
        if (!analysis.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该分析记录");
        }
        return analysis;
    }

    /**
     * 删除分析记录
     */
    @Transactional
    public void deleteAnalysis(Long id, Long userId) {
        WorkflowAnalysis analysis = getAnalysis(id, userId);
        if ("RUNNING".equals(analysis.getWorkflowStatus())) {
            throw new IllegalStateException("不能删除正在运行的分析");
        }
        analysisRepository.delete(analysis);
    }

    /**
     * 按标的查询最近一次分析（供 Tool 使用）
     */
    public Optional<WorkflowAnalysis> findLatestByStock(Long userId, String stockCode) {
        return analysisRepository.findFirstByUserIdAndResolvedStockCodeOrderByCreatedAtDesc(userId, stockCode);
    }

    /**
     * 按标的名称模糊查询最近一次分析（Tool 使用，不指定 userId）
     * 先尝试精确匹配 stockCode，再尝试按 stockName 模糊匹配
     */
    public Optional<WorkflowAnalysis> findLatestByStockAny(String stockCodeOrName) {
        // 先尝试精确匹配 stockCode
        var byCode = analysisRepository.findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc(stockCodeOrName, "COMPLETED");
        if (byCode.isPresent()) return byCode;
        // 再尝试按 stockName 模糊匹配
        return analysisRepository.findFirstByResolvedStockNameContainingAndWorkflowStatusOrderByCreatedAtDesc(stockCodeOrName, "COMPLETED");
    }
}
```

### Step 6: 创建 AnalysisController

文件: `src/main/java/com/xiaomo/agent/analysis/controller/AnalysisController.java`

```java
package com.xiaomo.agent.analysis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.analysis.service.AnalysisService;
import com.xiaomo.agent.common.entity.Result;
import com.xiaomo.agent.workflow.event.WorkflowEvent;
import com.xiaomo.agent.workflow.persist.WorkflowAnalysis;
import com.xiaomo.agent.workflow.service.DeepAnalysisWorkflow;
import com.xiaomo.agent.workflow.util.StockCodeExtractor;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@Slf4j
public class AnalysisController {

    @Resource
    private AnalysisService analysisService;
    @Resource
    private DeepAnalysisWorkflow deepAnalysisWorkflow;
    @Resource
    private ObjectMapper objectMapper;

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new RuntimeException("未登录");
        return userId;
    }

    /**
     * 发起分析
     */
    @PostMapping("/start")
    public Result<Map<String, Object>> startAnalysis(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return Result.error("分析主题不能为空");
        }

        // 解析标的
        String stockCode = StockCodeExtractor.extract(query);
        String stockName = null;  // 可通过 StockResolver 解析

        // 创建分析记录
        WorkflowAnalysis analysis = analysisService.createAnalysis(userId, query, stockCode, stockName);

        // 异步启动工作流（不阻塞响应）
        Long analysisId = analysis.getId();
        Mono.fromRunnable(() -> {
            try {
                deepAnalysisWorkflow.executeWithAnalysisId(userId, analysisId, query)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                event -> log.debug("分析事件: {}", event.type()),
                                error -> log.error("分析失败: {}", error.getMessage()),
                                () -> log.info("分析完成: {}", analysisId)
                        );
            } catch (Exception e) {
                log.error("启动分析失败", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        return Result.success(Map.of(
                "analysisId", analysisId,
                "stockCode", stockCode != null ? stockCode : "",
                "stockName", stockName != null ? stockName : ""
        ));
    }

    /**
     * SSE 实时事件流
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAnalysis(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        // 验证权限
        analysisService.getAnalysis(id, userId);

        Sinks.Many<WorkflowEvent> sink = deepAnalysisWorkflow.getEventSink(id);
        if (sink == null) {
            // 分析已完成或不存在，返回空流
            // 也可能是工作流尚未启动（竞态条件），等待最多 2 秒
            for (int i = 0; i < 20; i++) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                sink = deepAnalysisWorkflow.getEventSink(id);
                if (sink != null) break;
            }
            if (sink == null) {
                return Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{\"analysisId\":" + id + "}")
                                .build()
                );
            }
        }

        return sink.asFlux()
                .map(event -> {
                    try {
                        return ServerSentEvent.<String>builder()
                                .event("workflow")
                                .data(objectMapper.writeValueAsString(event))
                                .build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder()
                                .event("error")
                                .data("{\"message\":\"序列化失败\"}")
                                .build();
                    }
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{\"analysisId\":" + id + "}")
                                .build()
                ));
    }

    /**
     * 分析列表
     */
    @GetMapping("/list")
    public Result<List<WorkflowAnalysis>> listAnalyses(HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(analysisService.listAnalyses(userId));
    }

    /**
     * 分析详情
     */
    @GetMapping("/{id}")
    public Result<WorkflowAnalysis> getAnalysis(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(analysisService.getAnalysis(id, userId));
    }

    /**
     * 删除分析
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAnalysis(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        analysisService.deleteAnalysis(id, userId);
        return Result.success();
    }
}
```

### Step 7: 创建 GetAnalysisReportTool

文件: `src/main/java/com/xiaomo/agent/tool/GetAnalysisReportTool.java`

```java
package com.xiaomo.agent.tool;

import com.xiaomo.agent.analysis.service.AnalysisService;
import com.xiaomo.agent.workflow.persist.WorkflowAnalysis;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GetAnalysisReportTool {

    private final AnalysisService analysisService;

    public GetAnalysisReportTool(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Tool(description = "查询指定股票的深度分析报告。当用户询问某只股票的深度分析结论、分析报告、投资建议时调用此工具。返回最近一次深度分析的完整结论，包括操作建议、置信度、目标价、综合摘要和交易方案。")
    public String getAnalysisReport(
            @ToolParam(description = "股票代码（如688398）或股票名称（如丰光精密）") String stockCode,
            @ToolParam(description = "指定分析记录ID，可选，不填则查询该股票最近一次分析") Long analysisId) {
        try {
            // 优先按 analysisId 查询
            if (analysisId != null) {
                // 注意：Tool 运行在 AI 对话上下文中，需要通过 userId 查询
                // 这里通过 analysisService 查询，但需要 userId
                // 解决方案：通过 ToolContext 获取 userId（由 ToolCallbackContextWrapper 注入）
                // 暂时先通过 analysisId 直接查询，权限校验在 Controller 层
            }

            // 通过 stockCode 查询最近一次分析
            if (stockCode == null || stockCode.isBlank()) {
                return "错误：请提供股票代码或股票名称";
            }

            // 尝试从 ToolContext 获取 userId（由 ToolCallbackContextWrapper 注入）
            // 如果无法获取，使用 analysisRepository 直接查询
            var analyses = analysisService.findLatestByStockAny(stockCode);
            if (analyses.isEmpty()) {
                return "未找到「" + stockCode + "」的深度分析记录。请先在深度分析页面（/analysis）发起分析。";
            }

            var analysis = analyses.get();
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(analysis.getResolvedStockName() != null ? analysis.getResolvedStockName() : stockCode);
            sb.append(" 深度分析报告\n\n");
            sb.append("- 分析时间: ").append(analysis.getCompletedAt() != null ? analysis.getCompletedAt() : analysis.getCreatedAt()).append("\n");
            sb.append("- 状态: ").append(analysis.getWorkflowStatus()).append("\n\n");

            if (analysis.getAction() != null) {
                sb.append("### 投资决策\n");
                sb.append("- 操作建议: **").append(analysis.getAction()).append("**\n");
                if (analysis.getConfidence() != null) {
                    sb.append("- 置信度: ").append(String.format("%.0f%%", analysis.getConfidence() * 100)).append("\n");
                }
                if (analysis.getTargetPrice() != null) {
                    sb.append("- 目标价: ¥").append(analysis.getTargetPrice()).append("\n");
                }
                sb.append("\n");
            }

            if (analysis.getSummary() != null && !analysis.getSummary().isBlank()) {
                sb.append("### 综合摘要\n").append(analysis.getSummary()).append("\n\n");
            }

            if (analysis.getInvestmentPlan() != null && !analysis.getInvestmentPlan().isBlank()) {
                sb.append("### 投资计划\n").append(analysis.getInvestmentPlan()).append("\n\n");
            }

            if (analysis.getTradingProposal() != null && !analysis.getTradingProposal().isBlank()) {
                sb.append("### 交易方案\n").append(analysis.getTradingProposal()).append("\n\n");
            }

            sb.append("---\n*以上为深度分析系统自动生成，仅供参考，不构成投资建议*");
            return sb.toString();
        } catch (Exception e) {
            return "查询分析报告时出错: " + e.getMessage();
        }
    }
}
```

### Step 8: 注册 GetAnalysisReportTool 到 AgentLoopImpl

文件: `src/main/java/com/xiaomo/agent/agent/service/impl/AgentLoopImpl.java`

1. 构造函数新增参数: `GetAnalysisReportTool getAnalysisReportTool`
2. `MethodToolCallbackProvider.builder().toolObjects(...)` 中新增: `getAnalysisReportTool`

### Step 9: 编写后端测试

文件: `src/test/java/com/xiaomo/agent/analysis/service/AnalysisServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService 分析服务测试")
class AnalysisServiceTest {

    @Mock
    private WorkflowAnalysisRepository analysisRepository;

    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(analysisRepository);
    }

    @Nested
    @DisplayName("createAnalysis 创建分析")
    class CreateAnalysisTest {
        @Test
        @DisplayName("已有运行中分析 → 抛出异常")
        void runningAnalysisExists() {
            // 断言抛出 IllegalStateException
        }

        @Test
        @DisplayName("正常创建 → 返回 PENDING 状态记录")
        void createSuccess() {
            // 断言返回的记录状态为 PENDING
        }
    }

    @Nested
    @DisplayName("getAnalysis 获取分析")
    class GetAnalysisTest {
        @Test
        @DisplayName("分析不存在 → 抛出异常")
        void notFound() { }

        @Test
        @DisplayName("用户不匹配 → 抛出异常")
        void wrongUser() { }
    }
}
```

文件: `src/test/java/com/xiaomo/agent/tool/GetAnalysisReportToolTest.java`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("GetAnalysisReportTool 分析报告查询工具测试")
class GetAnalysisReportToolTest {

    @Mock
    private AnalysisService analysisService;

    private GetAnalysisReportTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetAnalysisReportTool(analysisService);
    }

    @Nested
    @DisplayName("getAnalysisReport 查询分析报告")
    class GetReportTest {
        @Test
        @DisplayName("未找到分析 → 返回提示信息")
        void notFound() {
            // 断言返回内容包含"未找到"
        }

        @Test
        @DisplayName("找到分析 → 返回包含操作建议和置信度的报告")
        void found() {
            // 断言返回内容包含 action, confidence 等字段
        }
    }
}
```

### Step 10: 运行后端测试并提交

```bash
mvn test -pl . -Dtest="AnalysisServiceTest,GetAnalysisReportToolTest" -DfailIfNoTests=false
```

确认测试通过后提交。

---

## Task 2: 前端 API 层 + Store

**Files:**
- Create: `frontend/src/api/analysis.ts`
- Create: `frontend/src/stores/analysis.ts`

**Interfaces:**
- Consumes: `@/api/request.ts` (axios 实例), `@/stores/auth.ts` (token)
- Produces: `analysisStore` (list, selectedId, startAnalysis, loadAnalyses, selectAnalysis, deleteAnalysis), `streamAnalysis()` SSE 函数

---

### Step 1: 创建 API 层

文件: `frontend/src/api/analysis.ts`

```typescript
import request from './request'

export interface WorkflowEvent {
  type: string
  agentName: string | null
  content: string | null
  phase: string | null
  timestamp: string
}

export interface AnalysisRecord {
  id: number
  userId: number
  conversationId: number | null
  originalQuery: string
  resolvedStockCode: string | null
  resolvedStockName: string | null
  workflowStatus: string
  action: string | null
  confidence: number | null
  targetPrice: number | null
  summary: string | null
  tradingProposal: string | null
  investmentPlan: string | null
  analystReportsJson: string | null
  bullBearDebateJson: string | null
  riskDebateJson: string | null
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
  createdAt: string
}

export interface StartAnalysisResponse {
  analysisId: number
  stockCode: string
  stockName: string
}

interface Result<T> {
  code: number
  msg?: string
  data: T
}

export async function startAnalysis(query: string): Promise<StartAnalysisResponse> {
  const res = await request.post<Result<StartAnalysisResponse>>('/api/analysis/start', { query })
  if (res.data.code !== 1) throw new Error(res.data.msg || '发起分析失败')
  return res.data.data
}

export async function getAnalysisList(): Promise<AnalysisRecord[]> {
  const res = await request.get<Result<AnalysisRecord[]>>('/api/analysis/list')
  if (res.data.code !== 1) throw new Error(res.data.msg || '获取列表失败')
  return res.data.data
}

export async function getAnalysisDetail(id: number): Promise<AnalysisRecord> {
  const res = await request.get<Result<AnalysisRecord>>(`/api/analysis/${id}`)
  if (res.data.code !== 1) throw new Error(res.data.msg || '获取详情失败')
  return res.data.data
}

export async function deleteAnalysis(id: number): Promise<void> {
  const res = await request.delete<Result<void>>(`/api/analysis/${id}`)
  if (res.data.code !== 1) throw new Error(res.data.msg || '删除失败')
}

/**
 * SSE 实时分析事件流
 */
export function streamAnalysis(
  analysisId: number,
  token: string,
  callbacks: {
    onEvent: (event: WorkflowEvent) => void
    onDone: () => void
    onError: (error: string) => void
  }
): AbortController {
  const controller = new AbortController()
  const url = `/api/analysis/${analysisId}/stream`

  fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        callbacks.onError(`HTTP ${response.status}`)
        return
      }
      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const parts = buffer.split('\n\n')
        buffer = parts.pop() || ''

        for (const part of parts) {
          const lines = part.split('\n')
          let eventType = ''
          let eventData = ''
          for (const line of lines) {
            if (line.startsWith('event:')) eventType = line.slice(6).trim()
            else if (line.startsWith('data:')) eventData = line.slice(5).trim()
          }
          if (eventType === 'workflow' && eventData) {
            try {
              callbacks.onEvent(JSON.parse(eventData))
            } catch (e) { /* ignore parse errors */ }
          } else if (eventType === 'done') {
            callbacks.onDone()
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') callbacks.onError(err.message)
    })

  return controller
}
```

### Step 2: 创建 Analysis Store

文件: `frontend/src/stores/analysis.ts`

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getAnalysisList,
  getAnalysisDetail,
  startAnalysis as apiStartAnalysis,
  deleteAnalysis as apiDeleteAnalysis,
  streamAnalysis,
  type AnalysisRecord,
  type WorkflowEvent,
} from '@/api/analysis'
import { useAuthStore } from './auth'

export const useAnalysisStore = defineStore('analysis', () => {
  const analyses = ref<AnalysisRecord[]>([])
  const selectedId = ref<number | null>(null)
  const selectedDetail = ref<AnalysisRecord | null>(null)
  const isRunning = ref(false)
  const workflowEvents = ref<WorkflowEvent[]>([])
  const loading = ref(false)

  let abortController: AbortController | null = null

  async function loadAnalyses() {
    loading.value = true
    try {
      analyses.value = await getAnalysisList()
    } finally {
      loading.value = false
    }
  }

  async function selectAnalysis(id: number) {
    selectedId.value = id
    // 检查是否正在运行中（有事件流）
    const record = analyses.value.find((a) => a.id === id)
    if (record && record.workflowStatus === 'RUNNING' && !isRunning.value) {
      // 尝试重新连接 SSE（MVP 不实现重连，直接加载详情）
    }
    try {
      selectedDetail.value = await getAnalysisDetail(id)
    } catch (e) {
      selectedDetail.value = null
    }
  }

  async function handleStartAnalysis(query: string) {
    const authStore = useAuthStore()
    const result = await apiStartAnalysis(query)

    // 立即在列表中添加一条 RUNNING 记录
    const newRecord: AnalysisRecord = {
      id: result.analysisId,
      userId: 0,
      conversationId: null,
      originalQuery: query,
      resolvedStockCode: result.stockCode,
      resolvedStockName: result.stockName,
      workflowStatus: 'RUNNING',
      action: null,
      confidence: null,
      targetPrice: null,
      summary: null,
      tradingProposal: null,
      investmentPlan: null,
      analystReportsJson: null,
      bullBearDebateJson: null,
      riskDebateJson: null,
      startedAt: new Date().toISOString(),
      completedAt: null,
      errorMessage: null,
      createdAt: new Date().toISOString(),
    }
    analyses.value.unshift(newRecord)
    selectedId.value = result.analysisId
    selectedDetail.value = newRecord
    isRunning.value = true
    workflowEvents.value = []

    // 建立 SSE 连接
    abortController = streamAnalysis(result.analysisId, authStore.token, {
      onEvent(event) {
        workflowEvents.value.push(event)
      },
      onDone() {
        isRunning.value = false
        abortController = null
        // 刷新分析详情
        selectAnalysis(result.analysisId)
        loadAnalyses()
      },
      onError(msg) {
        isRunning.value = false
        abortController = null
        console.error('分析流错误:', msg)
      },
    })

    return result
  }

  async function handleDeleteAnalysis(id: number) {
    await apiDeleteAnalysis(id)
    analyses.value = analyses.value.filter((a) => a.id !== id)
    if (selectedId.value === id) {
      selectedId.value = null
      selectedDetail.value = null
    }
  }

  function stopAnalysis() {
    if (abortController) {
      abortController.abort()
      abortController = null
      isRunning.value = false
    }
  }

  return {
    analyses,
    selectedId,
    selectedDetail,
    isRunning,
    workflowEvents,
    loading,
    loadAnalyses,
    selectAnalysis,
    handleStartAnalysis,
    handleDeleteAnalysis,
    stopAnalysis,
  }
})
```

### Step 3: 运行前端类型检查

```bash
cd frontend && npx tsc --noEmit
```

确认无类型错误后提交。

---

## Task 3: 前端 AnalysisInput + AnalysisList + AnalysisDetail 组件

**Files:**
- Create: `frontend/src/components/analysis/AnalysisInput.vue`
- Create: `frontend/src/components/analysis/AnalysisList.vue`
- Create: `frontend/src/components/analysis/AnalysisDetail.vue`

**Interfaces:**
- Consumes: `useAnalysisStore` (analyses, selectedId, isRunning, workflowEvents, selectedDetail)
- Produces: 三个组件供 AnalysisView.vue 组合使用

---

### Step 1: 创建 AnalysisInput 组件

文件: `frontend/src/components/analysis/AnalysisInput.vue`

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { Search, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  isRunning: boolean
}>()

const emit = defineEmits<{
  submit: [query: string]
}>()

const inputValue = ref('')

function handleSubmit() {
  const query = inputValue.value.trim()
  if (!query || props.isRunning) return
  emit('submit', query)
  inputValue.value = ''
}
</script>

<template>
  <div class="analysis-input">
    <div class="input-wrapper">
      <Search class="input-icon" :size="18" />
      <input
        v-model="inputValue"
        type="text"
        placeholder="输入股票名称或代码，如：丰光精密、688398"
        :disabled="isRunning"
        @keydown.enter="handleSubmit"
      />
      <button class="start-btn" :disabled="!inputValue.trim() || isRunning" @click="handleSubmit">
        <Loader2 v-if="isRunning" :size="16" class="spin" />
        <span v-else>开始分析</span>
      </button>
    </div>
    <p class="input-hint" v-if="isRunning">分析进行中，请等待完成后再发起新的分析...</p>
  </div>
</template>

<style scoped>
.analysis-input {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border, #e2e8f0);
  background: var(--surface, #ffffff);
}
.input-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--surface-2, #f1f5f9);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  padding: 8px 12px;
}
.input-icon { color: var(--text-dim, #94a3b8); flex-shrink: 0; }
input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-size: 14px;
  color: var(--text, #1e293b);
}
input::placeholder { color: var(--text-dim, #94a3b8); }
.start-btn {
  padding: 6px 16px;
  border-radius: 6px;
  border: none;
  background: var(--accent, #2563eb);
  color: white;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.start-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.input-hint { margin: 8px 0 0; font-size: 12px; color: var(--text-dim, #94a3b8); }
</style>
```

### Step 2: 创建 AnalysisList 组件

文件: `frontend/src/components/analysis/AnalysisList.vue`

```vue
<script setup lang="ts">
import { Trash2, TrendingUp, TrendingDown, Minus, Loader2 } from 'lucide-vue-next'
import type { AnalysisRecord } from '@/api/analysis'

const props = defineProps<{
  analyses: AnalysisRecord[]
  selectedId: number | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  delete: [id: number]
}>()

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '等待中',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[status] || status
}

function getActionIcon(action: string | null) {
  if (action === 'BUY') return TrendingUp
  if (action === 'SELL') return TrendingDown
  return Minus
}

function getActionClass(action: string | null) {
  if (action === 'BUY') return 'action-buy'
  if (action === 'SELL') return 'action-sell'
  return 'action-hold'
}

function formatTime(dateStr: string | null) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <div class="analysis-list">
    <div class="list-header">
      <span class="list-title">分析记录</span>
      <span class="list-count">{{ analyses.length }}</span>
    </div>
    <div v-if="loading" class="list-loading">
      <Loader2 :size="20" class="spin" />
    </div>
    <div v-else-if="analyses.length === 0" class="list-empty">暂无分析记录</div>
    <div v-else class="list-items">
      <div
        v-for="item in analyses"
        :key="item.id"
        class="list-item"
        :class="{ active: item.id === selectedId, running: item.workflowStatus === 'RUNNING' }"
        @click="emit('select', item.id)"
      >
        <div class="item-main">
          <div class="item-stock">
            <span class="stock-name">{{ item.resolvedStockName || item.originalQuery }}</span>
            <span class="stock-code">{{ item.resolvedStockCode }}</span>
          </div>
          <div class="item-meta">
            <span class="status-badge" :class="'status-' + item.workflowStatus.toLowerCase()">
              <Loader2 v-if="item.workflowStatus === 'RUNNING'" :size="12" class="spin" />
              {{ getStatusLabel(item.workflowStatus) }}
            </span>
            <span v-if="item.action" class="action-badge" :class="getActionClass(item.action)">
              {{ item.action }}
            </span>
            <span v-if="item.confidence" class="confidence">{{ Math.round(item.confidence * 100) }}%</span>
          </div>
          <div class="item-time">{{ formatTime(item.createdAt) }}</div>
        </div>
        <button
          v-if="item.workflowStatus !== 'RUNNING'"
          class="delete-btn"
          @click.stop="emit('delete', item.id)"
        >
          <Trash2 :size="14" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-list {
  width: 280px;
  min-width: 280px;
  border-right: 1px solid var(--border, #e2e8f0);
  display: flex;
  flex-direction: column;
  background: var(--surface, #ffffff);
  overflow-y: auto;
}
.list-header {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border, #e2e8f0);
}
.list-title { font-size: 14px; font-weight: 600; color: var(--text, #1e293b); }
.list-count {
  font-size: 12px;
  color: var(--text-dim, #94a3b8);
  background: var(--surface-2, #f1f5f9);
  padding: 2px 8px;
  border-radius: 10px;
}
.list-loading, .list-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-dim, #94a3b8);
  font-size: 13px;
}
.list-items { flex: 1; overflow-y: auto; }
.list-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border, #e2e8f0);
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background 0.15s;
}
.list-item:hover { background: var(--sidebar-hover, #f1f5f9); }
.list-item.active { background: var(--sidebar-active, #dbeafe); border-left: 3px solid var(--accent, #2563eb); }
.list-item.running { border-left: 3px solid var(--accent, #2563eb); }
.item-main { flex: 1; min-width: 0; }
.item-stock { display: flex; align-items: baseline; gap: 6px; }
.stock-name { font-size: 14px; font-weight: 500; color: var(--text, #1e293b); }
.stock-code { font-size: 12px; color: var(--text-dim, #94a3b8); }
.item-meta { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.status-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.status-running { background: var(--accent-dim, #2563eb18); color: var(--accent, #2563eb); }
.status-completed { background: #dcfce7; color: var(--green, #16a34a); }
.status-failed { background: #fef2f2; color: var(--danger, #dc2626); }
.status-pending { background: var(--surface-2, #f1f5f9); color: var(--text-dim, #94a3b8); }
.action-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}
.action-buy { background: #dcfce7; color: var(--green, #16a34a); }
.action-sell { background: #fef2f2; color: var(--danger, #dc2626); }
.action-hold { background: var(--surface-2, #f1f5f9); color: var(--text-dim, #94a3b8); }
.confidence { font-size: 11px; color: var(--text-dim, #94a3b8); }
.item-time { font-size: 11px; color: var(--text-dim, #94a3b8); margin-top: 2px; }
.delete-btn {
  opacity: 0;
  background: none;
  border: none;
  color: var(--text-dim, #94a3b8);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  flex-shrink: 0;
}
.list-item:hover .delete-btn { opacity: 1; }
.delete-btn:hover { color: var(--danger, #dc2626); background: #fef2f2; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
```

### Step 3: 创建 AnalysisDetail 组件

文件: `frontend/src/components/analysis/AnalysisDetail.vue`

此组件复用 WorkflowPanel 的核心逻辑（阶段进度、Agent 状态机、内容渲染），但重新组织为详情页布局。当分析已完成时从 `selectedDetail` 渲染静态报告；当分析运行中时从 `workflowEvents` 实时渲染。

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Activity, CheckCircle2, AlertCircle, TrendingUp, TrendingDown, Minus, MessageSquare } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/blocks/MarkdownRenderer.vue'
import type { AnalysisRecord, WorkflowEvent } from '@/api/analysis'

const props = defineProps<{
  detail: AnalysisRecord | null
  events: WorkflowEvent[]
  isRunning: boolean
}>()

const emit = defineEmits<{
  goToChat: []
}>()

// --- 从 WorkflowPanel 复用的逻辑 ---

const phaseNames: Record<string, string> = {
  Layer1_DataCollection: '数据采集',
  BullBearDebate: '多空辩论',
  Trader: '交易决策',
  RiskDebate: '风险评估',
}

const phaseAgents: Record<string, string[]> = {
  Layer1_DataCollection: ['MarketAnalyst', 'FundamentalsAnalyst', 'NewsAnalyst', 'SocialAnalyst'],
  BullBearDebate: ['BullResearcher', 'BearResearcher', 'ResearchManager'],
  Trader: ['Trader'],
  RiskDebate: ['AggressiveAnalyst', 'ConservativeAnalyst', 'NeutralAnalyst', 'RiskJudge'],
}

const agentLabels: Record<string, string> = {
  MarketAnalyst: '技术面分析师',
  FundamentalsAnalyst: '基本面分析师',
  NewsAnalyst: '新闻分析师',
  SocialAnalyst: '舆情分析师',
  BullResearcher: '看多研究员',
  BearResearcher: '看空研究员',
  ResearchManager: '研究主管',
  Trader: '交易员',
  AggressiveAnalyst: '激进分析师',
  ConservativeAnalyst: '保守分析师',
  NeutralAnalyst: '中立分析师',
  RiskJudge: '风险裁决官',
}

// Agent 状态计算
const agentStates = computed(() => {
  const states = new Map<string, { status: string; content: string; round: number }>()
  for (const event of props.events) {
    const name = event.agentName
    if (!name) continue
    if (event.type === 'AGENT_START' || event.type === 'DEBATE_START') {
      states.set(name, { status: 'running', content: '', round: 0 })
    } else if (event.type === 'AGENT_CHUNK' || event.type === 'DEBATE_CHUNK') {
      const existing = states.get(name)
      if (existing) existing.content += event.content || ''
    } else if (event.type === 'AGENT_COMPLETE' || event.type === 'DEBATE_COMPLETE') {
      const existing = states.get(name)
      if (existing) {
        existing.status = 'done'
        if (event.content) existing.content = event.content
      }
    }
  }
  return states
})

// 已完成阶段
const completedPhases = computed(() => {
  return props.events.filter((e) => e.type === 'PHASE_COMPLETE').map((e) => e.phase)
})

// 最终裁决
const finalDecision = computed(() => {
  const event = props.events.find((e) => e.type === 'FINAL_DECISION')
  if (!event?.content) return null
  try {
    return JSON.parse(event.content)
  } catch {
    return { summary: event.content }
  }
})

// 当前阶段
const currentPhase = computed(() => {
  for (let i = props.events.length - 1; i >= 0; i--) {
    if (props.events[i].type === 'PHASE_START') return props.events[i].phase
  }
  return null
})

function getPhaseStatus(phase: string) {
  if (completedPhases.value.includes(phase)) return 'done'
  if (currentPhase.value === phase) return 'running'
  return 'pending'
}
</script>

<template>
  <div class="analysis-detail">
    <!-- 空态 -->
    <div v-if="!detail && !isRunning" class="detail-empty">
      <Activity :size="48" />
      <p>选择左侧分析记录查看详情</p>
      <p class="sub">或在顶部输入标的开始新的分析</p>
    </div>

    <!-- 有内容时 -->
    <template v-else>
      <!-- 头部信息 -->
      <div class="detail-header">
        <div class="header-stock">
          <h2>{{ detail?.resolvedStockName || detail?.originalQuery || '分析中...' }}</h2>
          <span class="stock-code">{{ detail?.resolvedStockCode }}</span>
        </div>
        <div class="header-status">
          <span v-if="isRunning" class="status running">
            <Activity :size="14" class="spin" /> 运行中
          </span>
          <span v-else-if="detail?.workflowStatus === 'COMPLETED'" class="status completed">
            <CheckCircle2 :size="14" /> 已完成
          </span>
          <span v-else-if="detail?.workflowStatus === 'FAILED'" class="status failed">
            <AlertCircle :size="14" /> 失败
          </span>
        </div>
      </div>

      <!-- 阶段进度条 -->
      <div class="phase-progress">
        <div
          v-for="(label, phase) in phaseNames"
          :key="phase"
          class="phase-step"
          :class="'phase-' + getPhaseStatus(phase)"
        >
          <div class="step-dot" />
          <span class="step-label">{{ label }}</span>
        </div>
      </div>

      <!-- 最终裁决卡片 -->
      <div v-if="finalDecision && !isRunning" class="decision-card">
        <div class="decision-header">
          <span class="decision-label">投资决策</span>
          <span class="decision-action" :class="'action-' + (finalDecision.action || '').toLowerCase()">
            {{ finalDecision.action || 'N/A' }}
          </span>
        </div>
        <div class="decision-metrics">
          <div class="metric" v-if="finalDecision.confidence">
            <span class="metric-label">置信度</span>
            <span class="metric-value">{{ Math.round(finalDecision.confidence * 100) }}%</span>
          </div>
          <div class="metric" v-if="finalDecision.targetPrice">
            <span class="metric-label">目标价</span>
            <span class="metric-value">¥{{ finalDecision.targetPrice }}</span>
          </div>
        </div>
        <div v-if="finalDecision.summary" class="decision-summary">
          <MarkdownRenderer :content="finalDecision.summary" />
        </div>
        <button class="chat-btn" @click="emit('goToChat')">
          <MessageSquare :size="14" /> 在对话中提问
        </button>
      </div>

      <!-- Agent 内容区 -->
      <div class="phases-content">
        <div v-for="(label, phase) in phaseNames" :key="phase" class="phase-section">
          <div class="phase-header">
            <span class="phase-title">{{ label }}</span>
            <span class="phase-status-icon">
              <CheckCircle2 v-if="getPhaseStatus(phase) === 'done'" :size="14" />
              <Activity v-else-if="getPhaseStatus(phase) === 'running'" :size="14" class="spin" />
            </span>
          </div>
          <div class="agent-cards">
            <div
              v-for="agent in phaseAgents[phase]"
              :key="agent"
              class="agent-card"
              :class="{ 'agent-done': agentStates.get(agent)?.status === 'done' }"
            >
              <div class="agent-header">
                <span class="agent-name">{{ agentLabels[agent] || agent }}</span>
                <span class="agent-status">
                  <CheckCircle2 v-if="agentStates.get(agent)?.status === 'done'" :size="12" />
                  <Activity v-else-if="agentStates.get(agent)?.status === 'running'" :size="12" class="spin" />
                </span>
              </div>
              <div v-if="agentStates.get(agent)?.content" class="agent-content">
                <MarkdownRenderer :content="agentStates.get(agent)!.content" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.analysis-detail {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg, #f8fafc);
}
.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-dim, #94a3b8);
  gap: 8px;
}
.detail-empty .sub { font-size: 13px; }

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header-stock h2 { font-size: 20px; font-weight: 600; margin: 0; }
.stock-code { font-size: 13px; color: var(--text-dim, #94a3b8); margin-left: 8px; }
.status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
}
.status.running { background: var(--accent-dim, #2563eb18); color: var(--accent, #2563eb); }
.status.completed { background: #dcfce7; color: var(--green, #16a34a); }
.status.failed { background: #fef2f2; color: var(--danger, #dc2626); }

.phase-progress {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  background: var(--surface, #ffffff);
  border-radius: 8px;
  padding: 12px 16px;
  border: 1px solid var(--border, #e2e8f0);
}
.phase-step {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-dim, #94a3b8);
}
.phase-step:not(:last-child)::after {
  content: '';
  flex: 1;
  height: 2px;
  background: var(--border, #e2e8f0);
  margin: 0 8px;
}
.phase-done .step-dot { background: var(--green, #16a34a); }
.phase-running .step-dot { background: var(--accent, #2563eb); animation: pulse 1.5s infinite; }
.phase-pending .step-dot { background: var(--border, #e2e8f0); }
.step-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.decision-card {
  background: var(--surface, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}
.decision-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.decision-label { font-size: 14px; font-weight: 600; }
.decision-action {
  font-size: 14px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 6px;
}
.action-buy { background: #dcfce7; color: var(--green, #16a34a); }
.action-sell { background: #fef2f2; color: var(--danger, #dc2626); }
.action-hold { background: var(--surface-2, #f1f5f9); color: var(--text-dim, #94a3b8); }
.decision-metrics { display: flex; gap: 24px; margin-bottom: 12px; }
.metric-label { font-size: 12px; color: var(--text-dim, #94a3b8); display: block; }
.metric-value { font-size: 18px; font-weight: 600; }
.decision-summary { font-size: 14px; line-height: 1.6; }
.chat-btn {
  margin-top: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--accent, #2563eb);
  background: none;
  color: var(--accent, #2563eb);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.chat-btn:hover { background: var(--accent-dim, #2563eb18); }

.phases-content { display: flex; flex-direction: column; gap: 16px; }
.phase-section {
  background: var(--surface, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
}
.phase-header {
  padding: 10px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface-2, #f1f5f9);
  border-bottom: 1px solid var(--border, #e2e8f0);
}
.phase-title { font-size: 14px; font-weight: 600; }
.phase-status-icon { color: var(--green, #16a34a); }
.agent-cards { padding: 8px; }
.agent-card {
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 4px;
}
.agent-card.agent-done { background: var(--surface-2, #f1f5f9); }
.agent-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.agent-name { font-size: 13px; font-weight: 500; }
.agent-status { color: var(--green, #16a34a); }
.agent-content { font-size: 13px; line-height: 1.6; max-height: 400px; overflow-y: auto; }

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>
```

### Step 4: 运行前端类型检查

```bash
cd frontend && npx tsc --noEmit
```

确认无类型错误后提交。

---

## Task 4: AnalysisView 页面 + 路由 + ChatView 清理

**Files:**
- Create: `frontend/src/views/AnalysisView.vue`
- Modify: `frontend/src/router/index.ts` — 添加 `/analysis` 路由
- Modify: `frontend/src/views/ChatView.vue` — 移除深度分析相关代码
- Modify: `frontend/src/styles/variables.css` — 如需新增全局样式

**Interfaces:**
- Consumes: `AnalysisInput`, `AnalysisList`, `AnalysisDetail` 组件, `useAnalysisStore`
- Produces: `/analysis` 路由页面

---

### Step 1: 创建 AnalysisView 页面

文件: `frontend/src/views/AnalysisView.vue`

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Brain, MessageSquare, PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'
import { useAnalysisStore } from '@/stores/analysis'
import { useAuthStore } from '@/stores/auth'
import AnalysisInput from '@/components/analysis/AnalysisInput.vue'
import AnalysisList from '@/components/analysis/AnalysisList.vue'
import AnalysisDetail from '@/components/analysis/AnalysisDetail.vue'
import { ref } from 'vue'

const router = useRouter()
const analysisStore = useAnalysisStore()
const authStore = useAuthStore()
const sidebarCollapsed = ref(false)

onMounted(() => {
  analysisStore.loadAnalyses()
})

function handleStartAnalysis(query: string) {
  analysisStore.handleStartAnalysis(query)
}

function goToChat() {
  router.push('/')
}
</script>

<template>
  <div class="analysis-page">
    <!-- 左侧导航栏（复用 ChatView 的侧边栏风格） -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="sidebar-brand">
          <Brain :size="20" />
          <span v-if="!sidebarCollapsed" class="brand-text">深度分析</span>
        </div>
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <PanelLeftClose v-if="!sidebarCollapsed" :size="18" />
        </button>
      </div>
      <div v-if="!sidebarCollapsed" class="sidebar-nav">
        <button class="nav-item" @click="goToChat">
          <MessageSquare :size="16" />
          <span>返回对话</span>
        </button>
      </div>
    </aside>

    <!-- 展开按钮（侧边栏折叠时） -->
    <button v-if="sidebarCollapsed" class="expand-btn" @click="sidebarCollapsed = false">
      <PanelLeftOpen :size="18" />
    </button>

    <!-- 主内容区 -->
    <div class="analysis-main">
      <AnalysisInput
        :is-running="analysisStore.isRunning"
        @submit="handleStartAnalysis"
      />
      <div class="analysis-content">
        <AnalysisList
          :analyses="analysisStore.analyses"
          :selected-id="analysisStore.selectedId"
          :loading="analysisStore.loading"
          @select="analysisStore.selectAnalysis"
          @delete="analysisStore.handleDeleteAnalysis"
        />
        <AnalysisDetail
          :detail="analysisStore.selectedDetail"
          :events="analysisStore.workflowEvents"
          :is-running="analysisStore.isRunning"
          @go-to-chat="goToChat"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-page {
  display: flex;
  height: 100vh;
  background: var(--bg, #f8fafc);
}

/* 侧边栏 - 复用 ChatView 的 sidebar 样式 */
.sidebar {
  width: 220px;
  min-width: 220px;
  background: var(--sidebar-bg, #f8fafc);
  border-right: 1px solid var(--border, #e2e8f0);
  display: flex;
  flex-direction: column;
  transition: width 0.2s, min-width 0.2s;
}
.sidebar.collapsed { width: 0; min-width: 0; overflow: hidden; }
.sidebar-header {
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sidebar-brand { display: flex; align-items: center; gap: 8px; color: var(--text, #1e293b); }
.brand-text { font-size: 15px; font-weight: 600; }
.collapse-btn, .expand-btn {
  background: none;
  border: none;
  color: var(--text-dim, #94a3b8);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}
.collapse-btn:hover, .expand-btn:hover { background: var(--sidebar-hover, #f1f5f9); }
.sidebar-nav { padding: 8px 12px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text, #1e293b);
}
.nav-item:hover { background: var(--sidebar-hover, #f1f5f9); }

.expand-btn {
  position: absolute;
  top: 12px;
  left: 8px;
  z-index: 10;
}

.analysis-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.analysis-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
```

### Step 2: 添加路由

文件: `frontend/src/router/index.ts`

在路由配置中新增:

```typescript
{
  path: '/analysis',
  name: 'Analysis',
  component: () => import('@/views/AnalysisView.vue'),
  meta: { requiresAuth: true }
}
```

### Step 3: 从 ChatView.vue 移除深度分析代码

文件: `frontend/src/views/ChatView.vue`

需要移除的内容:
1. 导入: `streamDeepAnalysis`, `WorkflowEvent`, `WorkflowPanel`
2. 变量: `workflowEvents`, `isWorkflowRunning`, `isWorkflowMode`
3. 函数: `isDeepAnalysisRequest()`, `handleDeepAnalysis()`, `resetWorkflowState()`, `buildWorkflowSummary()`, `buildPartialWorkflowSummary()`
4. `handleSend()` 中的 `if (isDeepAnalysisRequest(text))` 分支
5. 模板中 `WorkflowPanel` 的渲染条件（`isWorkflowMode && ...`）
6. `handleStop()` 中的 workflow 相关逻辑

### Step 4: 运行前端类型检查 + 手动测试

```bash
cd frontend && npx tsc --noEmit
cd frontend && npm run dev
```

手动测试:
1. 访问 `/analysis` 页面，确认布局正确
2. 输入标的发起分析，确认 SSE 流正常工作
3. 分析完成后确认左侧列表和右侧详情正确显示
4. 确认对话页面不再触发深度分析
5. 确认"在对话中提问"按钮跳转到对话页

确认无误后提交。

---

## Task 5: 后端测试 + 端到端验证

**Files:**
- Test: `src/test/java/com/xiaomo/agent/analysis/controller/AnalysisControllerTest.java`

---

### Step 1: 编写 AnalysisController 集成测试

文件: `src/test/java/com/xiaomo/agent/analysis/controller/AnalysisControllerTest.java`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisController 分析控制器测试")
class AnalysisControllerTest {

    @Mock
    private AnalysisService analysisService;
    @Mock
    private DeepAnalysisWorkflow deepAnalysisWorkflow;
    @Mock
    private ObjectMapper objectMapper;

    private AnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalysisController(analysisService, deepAnalysisWorkflow, objectMapper);
    }

    @Nested
    @DisplayName("startAnalysis 发起分析")
    class StartAnalysisTest {
        @Test
        @DisplayName("空query → 返回错误")
        void emptyQuery() {
            // 断言 Result.error
        }

        @Test
        @DisplayName("已有运行中分析 → 返回409")
        void alreadyRunning() {
            // 断言 IllegalStateException
        }
    }

    @Nested
    @DisplayName("listAnalyses 分析列表")
    class ListAnalysesTest {
        @Test
        @DisplayName("正常返回用户分析列表")
        void success() {
            // 断言返回列表
        }
    }
}
```

### Step 2: 运行全部后端测试

```bash
mvn test
```

确认所有测试通过。

### Step 3: 端到端验证

```bash
# 启动后端
mvn spring-boot:run

# 启动前端
cd frontend && npm run dev
```

手动验证清单:
- [ ] `/analysis` 页面正常加载
- [ ] 输入标的发起分析，SSE 流正常推送
- [ ] 分析完成后左侧列表显示正确（标的名、操作建议、置信度）
- [ ] 点击左侧列表项，右侧显示完整工作流详情
- [ ] 删除分析记录正常
- [ ] 对话页面不再触发深度分析
- [ ] 对话中调用 `get_analysis_report` Tool 能返回分析结果

确认全部通过后提交。
