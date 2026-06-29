package com.itlk.myclaudecode.workflow.state;

import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import lombok.Data;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class WorkflowState {

    // 输入
    private Long userId;
    private Long conversationId;
    private String originalQuery;

    // Layer 1 输出（并行写入）
    private Map<String, AgentReport> analystReports = new ConcurrentHashMap<>();

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
}
