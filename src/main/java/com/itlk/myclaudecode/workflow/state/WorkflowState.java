package com.itlk.myclaudecode.workflow.state;

import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import lombok.Data;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class WorkflowState {

    // 输入
    private Long userId;
    private Long conversationId;
    private Long analysisId;  // 独立分析模式的分析记录 ID
    private String originalQuery;
    private Set<String> allowedStockCodes;

    // 标的锁定（由 StockResolver 在工作流启动时写入，之后只读）
    private String resolvedStockCode;   // 锁定的6位代码，如 "430510"
    private String resolvedStockName;   // 锁定的名称，如 "丰光精密"

    // Layer 1 输出（并行写入）
    private Map<String, AgentReport> analystReports = new ConcurrentHashMap<>();

    // 缓存数据（供下游 Agent 复用，避免重复调用工具）
    private Map<String, String> cachedData = new ConcurrentHashMap<>();

    // Layer 2 输出
    private List<DebateMessage> bullBearDebate = new CopyOnWriteArrayList<>();
    private String investmentPlan;

    // Layer 3 输出
    private String tradingProposal;

    // Layer 4 输出
    private List<DebateMessage> riskDebate = new CopyOnWriteArrayList<>();
    private FinalDecision finalDecision;

    // 引擎内部
    private volatile String currentPhase;
    private Instant startTime = Instant.now();
    private transient Sinks.Many<WorkflowEvent> eventSink;

    // 取消检查回调（由 DeepAnalysisWorkflow 注入，引擎节点执行前调用）
    private transient java.util.function.BooleanSupplier cancelledChecker;

    // 取消信号 Sink（cancelAnalysis 时 emit，引擎 takeUntilOther 立即终止）
    private transient Sinks.One<Void> cancelSink;

    public boolean isCancelled() {
        return cancelledChecker != null && cancelledChecker.getAsBoolean();
    }

    public Sinks.One<Void> getOrCreateCancelSink() {
        if (cancelSink == null) {
            cancelSink = Sinks.one();
        }
        return cancelSink;
    }

    public void signalCancel() {
        if (cancelSink != null) {
            cancelSink.tryEmitValue(null);
        }
    }
}
