package com.itlk.myclaudecode.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.workflow.agent.AgentRole;
import com.itlk.myclaudecode.workflow.agent.WorkflowAgentFactory;
import com.itlk.myclaudecode.workflow.config.WorkflowProperties;
import com.itlk.myclaudecode.workflow.engine.WorkflowEngine;
import com.itlk.myclaudecode.workflow.engine.WorkflowGraph;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.node.*;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysisRepository;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DeepAnalysisWorkflow {

    private final WorkflowAgentFactory agentFactory;
    private final WorkflowEngine engine;
    private final WorkflowAnalysisRepository analysisRepository;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public DeepAnalysisWorkflow(WorkflowAgentFactory agentFactory,
                                 WorkflowEngine engine,
                                 WorkflowAnalysisRepository analysisRepository,
                                 WorkflowProperties properties,
                                 ObjectMapper objectMapper) {
        this.agentFactory = agentFactory;
        this.engine = engine;
        this.analysisRepository = analysisRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行深度分析工作流
     */
    public Flux<WorkflowEvent> execute(Long userId, Long conversationId, String query) {
        log.info("启动深度分析工作流: userId={}, query={}", userId, query);

        WorkflowState state = new WorkflowState();
        state.setUserId(userId);
        state.setConversationId(conversationId);
        state.setOriginalQuery(query);

        WorkflowGraph graph = buildGraph();

        return engine.execute(graph, state)
                .doOnComplete(() -> {
                    log.info("工作流完成，持久化结果");
                    persistResults(state);
                })
                .doOnError(e -> log.error("工作流执行错误: {}", e.getMessage(), e));
    }

    private WorkflowGraph buildGraph() {
        // Layer 1: 4 个并行分析师
        ParallelFanOutNode layer1 = new ParallelFanOutNode("Layer1_DataCollection",
                List.of(
                        agentFactory.createAnalyst(AgentRole.MARKET_ANALYST),
                        agentFactory.createAnalyst(AgentRole.FUNDAMENTALS_ANALYST),
                        agentFactory.createAnalyst(AgentRole.NEWS_ANALYST),
                        agentFactory.createAnalyst(AgentRole.SOCIAL_ANALYST)
                ));

        // Layer 2: 多空辩论
        BullBearDebateOrchestrator layer2 = new BullBearDebateOrchestrator(
                agentFactory.createDebateNode(AgentRole.BULL_RESEARCHER),
                agentFactory.createDebateNode(AgentRole.BEAR_RESEARCHER),
                agentFactory.createJudgeNode(AgentRole.RESEARCH_MANAGER),
                properties.bullBearRounds());

        // Layer 3: 交易员
        TraderNode layer3 = agentFactory.createTraderNode();

        // Layer 4: 风险评估辩论
        RiskDebateOrchestrator layer4 = new RiskDebateOrchestrator(
                agentFactory.createDebateNode(AgentRole.AGGRESSIVE_ANALYST),
                agentFactory.createDebateNode(AgentRole.CONSERVATIVE_ANALYST),
                agentFactory.createDebateNode(AgentRole.NEUTRAL_ANALYST),
                agentFactory.createJudgeNode(AgentRole.RISK_JUDGE),
                properties.riskRounds());

        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(layer1)
                .addNode(layer2)
                .addNode(layer3)
                .addNode(layer4)
                .addEdge("Layer1_DataCollection", "BullBearDebate")
                .addEdge("BullBearDebate", "Trader")
                .addEdge("Trader", "RiskDebate")
                .setStart("Layer1_DataCollection");

        return graph;
    }

    /**
     * 从已持久化的 WorkflowAnalysis 构建聊天摘要（用于保存到 chat_messages）
     */
    public String buildSummaryForConversation(Long conversationId) {
        List<WorkflowAnalysis> analyses = analysisRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        if (analyses.isEmpty()) {
            return "## 深度分析完成\n\n分析结果已生成，请查看工作流详情。";
        }
        WorkflowAnalysis latest = analyses.get(0);

        StringBuilder sb = new StringBuilder();
        sb.append("## 深度分析完成\n\n");

        if (latest.getAction() != null) {
            sb.append("### 投资决策\n\n");
            sb.append("| 项目 | 结论 |\n|------|------|\n");
            sb.append("| 操作建议 | **").append(latest.getAction()).append("** |\n");
            if (latest.getConfidence() != null) {
                sb.append("| 置信度 | ").append(String.format("%.0f%%", latest.getConfidence() * 100)).append(" |\n");
            }
            if (latest.getTargetPrice() != null) {
                sb.append("| 目标价 | ").append(latest.getTargetPrice()).append(" |\n");
            }
            sb.append("\n");
        }

        if (latest.getSummary() != null && !latest.getSummary().isBlank()) {
            sb.append("### 综合摘要\n\n").append(latest.getSummary()).append("\n\n");
        }

        if (latest.getTradingProposal() != null && !latest.getTradingProposal().isBlank()) {
            sb.append("### 交易方案\n\n").append(latest.getTradingProposal()).append("\n\n");
        }

        sb.append("---\n*以上由多智能体工作流自动生成，仅供参考*");
        return sb.toString();
    }

    private void persistResults(WorkflowState state) {
        try {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setUserId(state.getUserId());
            analysis.setConversationId(state.getConversationId());
            analysis.setOriginalQuery(state.getOriginalQuery());

            // 分析师报告
            Map<String, String> reportsMap = new HashMap<>();
            state.getAnalystReports().forEach((k, v) -> reportsMap.put(k, v.reportContent()));
            analysis.setAnalystReportsJson(objectMapper.writeValueAsString(reportsMap));

            // 辩论记录
            analysis.setBullBearDebateJson(objectMapper.writeValueAsString(state.getBullBearDebate()));
            analysis.setInvestmentPlan(state.getInvestmentPlan());
            analysis.setTradingProposal(state.getTradingProposal());
            analysis.setRiskDebateJson(objectMapper.writeValueAsString(state.getRiskDebate()));

            // 最终裁决
            if (state.getFinalDecision() != null) {
                analysis.setAction(state.getFinalDecision().action());
                analysis.setConfidence(state.getFinalDecision().confidence());
                analysis.setTargetPrice(state.getFinalDecision().targetPrice());
                analysis.setSummary(state.getFinalDecision().summary());
            }

            analysisRepository.save(analysis);
            log.info("工作流结果已持久化，ID={}", analysis.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化工作流结果失败", e);
        } catch (Exception e) {
            log.error("持久化工作流结果失败", e);
        }
    }
}
