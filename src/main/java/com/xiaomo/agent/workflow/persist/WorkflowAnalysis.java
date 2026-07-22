package com.xiaomo.agent.workflow.persist;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_analyses")
@Data
public class WorkflowAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "original_query", columnDefinition = "TEXT")
    private String originalQuery;

    @Column(name = "analyst_reports_json", columnDefinition = "TEXT")
    private String analystReportsJson;

    @Column(name = "bull_bear_debate_json", columnDefinition = "TEXT")
    private String bullBearDebateJson;

    @Column(name = "investment_plan", columnDefinition = "TEXT")
    private String investmentPlan;

    @Column(name = "trading_proposal", columnDefinition = "TEXT")
    private String tradingProposal;

    @Column(name = "risk_debate_json", columnDefinition = "TEXT")
    private String riskDebateJson;

    @Column(length = 20)
    private String action;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "target_price")
    private Double targetPrice;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "resolved_stock_code", length = 10)
    private String resolvedStockCode;

    @Column(name = "resolved_stock_name", length = 50)
    private String resolvedStockName;

    @Column(name = "workflow_status", length = 20)
    private String workflowStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "total_tool_calls")
    private Integer totalToolCalls;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
