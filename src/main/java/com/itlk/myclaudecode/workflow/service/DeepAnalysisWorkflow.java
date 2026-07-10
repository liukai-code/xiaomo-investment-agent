package com.itlk.myclaudecode.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.workflow.agent.AgentRole;
import com.itlk.myclaudecode.user.config.UserConfigService;
import com.itlk.myclaudecode.workflow.agent.WorkflowAgentFactory;
import com.itlk.myclaudecode.workflow.config.RiskOverrideProperties;
import com.itlk.myclaudecode.workflow.config.WorkflowProperties;
import com.itlk.myclaudecode.workflow.engine.WorkflowEngine;
import com.itlk.myclaudecode.workflow.engine.WorkflowGraph;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.node.*;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysisRepository;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import com.itlk.myclaudecode.workflow.util.StockCodeExtractor;
import com.itlk.myclaudecode.workflow.util.StockResolver;
import com.itlk.myclaudecode.common.config.HttpClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DeepAnalysisWorkflow {

    private final WorkflowAgentFactory agentFactory;
    private final WorkflowEngine engine;
    private final WorkflowAnalysisRepository analysisRepository;
    private final WorkflowProperties properties;
    private final RiskOverrideProperties riskOverrideProperties;
    private final ObjectMapper objectMapper;
    private final HttpClientService httpClientService;
    private final UserConfigService userConfigService;

    /** 独立分析模式的事件 Sink 映射（analysisId -> Sink） */
    private final ConcurrentHashMap<Long, Sinks.Many<WorkflowEvent>> analysisEventSinks = new ConcurrentHashMap<>();

    /** 已取消的分析 ID 集合（防止工作流完成后覆盖状态） */
    private final java.util.Set<Long> cancelledAnalyses = ConcurrentHashMap.newKeySet();

    public DeepAnalysisWorkflow(WorkflowAgentFactory agentFactory,
                                 WorkflowEngine engine,
                                 WorkflowAnalysisRepository analysisRepository,
                                 WorkflowProperties properties,
                                 RiskOverrideProperties riskOverrideProperties,
                                 ObjectMapper objectMapper,
                                 HttpClientService httpClientService,
                                 UserConfigService userConfigService) {
        this.agentFactory = agentFactory;
        this.engine = engine;
        this.analysisRepository = analysisRepository;
        this.properties = properties;
        this.riskOverrideProperties = riskOverrideProperties;
        this.objectMapper = objectMapper;
        this.httpClientService = httpClientService;
        this.userConfigService = userConfigService;
    }

    /**
     * 获取指定分析的事件 Sink（供 Controller 订阅）
     */
    public Sinks.Many<WorkflowEvent> getEventSink(Long analysisId) {
        return analysisEventSinks.get(analysisId);
    }

    /**
     * 移除事件 Sink（分析完成后清理）
     */
    public void removeEventSink(Long analysisId) {
        Sinks.Many<WorkflowEvent> sink = analysisEventSinks.remove(analysisId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /**
     * 取消正在运行的分析
     * 标记为已取消、移除 Sink、更新数据库状态为 FAILED
     */
    public void cancelAnalysis(Long analysisId) {
        cancelledAnalyses.add(analysisId);
        removeEventSink(analysisId);
        analysisRepository.findById(analysisId).ifPresent(a -> {
            a.setWorkflowStatus("FAILED");
            a.setErrorMessage("用户手动停止");
            a.setCompletedAt(java.time.LocalDateTime.now());
            analysisRepository.save(a);
        });
        log.info("分析已取消: analysisId={}", analysisId);
    }

    /**
     * 执行深度分析工作流（对话模式，关联 conversationId）
     */
    public Flux<WorkflowEvent> execute(Long userId, Long conversationId, String query) {
        log.info("启动深度分析工作流: userId={}, query={}", userId, query);

        WorkflowState state = new WorkflowState();
        state.setUserId(userId);
        state.setConversationId(conversationId);
        state.setOriginalQuery(query);

        // 提取股票代码用于范围守卫
        var stockCodes = StockCodeExtractor.extract(query);
        if (!stockCodes.isEmpty()) {
            // 数字代码直接锁定
            String code = stockCodes.iterator().next();
            state.setResolvedStockCode(code);
            state.setAllowedStockCodes(stockCodes);
            log.info("从查询中提取到数字代码: {}", code);
        } else {
            // 无数字代码，走名称解析器
            try {
                var resolved = StockResolver.resolve(query, httpClientService);
                state.setResolvedStockCode(resolved.code());
                state.setResolvedStockName(resolved.name());
                state.setAllowedStockCodes(java.util.Set.of(resolved.code()));
                log.info("标的解析成功: {}({})", resolved.name(), resolved.code());
            } catch (IllegalArgumentException e) {
                log.warn("标的解析失败: {}", e.getMessage());
                return Flux.just(WorkflowEvent.error("标的解析失败: " + e.getMessage()));
            }
        }

        // 解析用户级 ChatModel（Per-User API Key 路由）
        ChatModel userChatModel = null;
        try {
            userChatModel = userConfigService.getUserChatModel(userId);
            if (userChatModel != null) {
                log.info("[DeepAnalysis] 使用用户自定义配置, userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("[DeepAnalysis] 获取用户级 ChatModel 失败, 使用全局配置, userId={}: {}", userId, e.getMessage());
        }

        WorkflowGraph graph = buildGraph(userChatModel);

        return engine.execute(graph, state)
                .doOnNext(event -> {
                    // 如果存在独立分析的 Sink，同步发布事件
                    Long analysisId = state.getAnalysisId();
                    if (analysisId != null) {
                        Sinks.Many<WorkflowEvent> sink = analysisEventSinks.get(analysisId);
                        if (sink != null && sink.currentSubscriberCount() > 0) {
                            sink.tryEmitNext(event);
                        }
                    }
                })
                .doOnComplete(() -> {
                    log.info("工作流完成，持久化结果");
                    if (state.getAnalysisId() != null) {
                        persistResultsWithId(state, state.getAnalysisId(), "COMPLETED", null);
                        removeEventSink(state.getAnalysisId());
                    } else {
                        persistResults(state, "COMPLETED", null);
                    }
                })
                .doOnError(e -> {
                    log.error("工作流执行错误: {}", e.getMessage(), e);
                    if (state.getAnalysisId() != null) {
                        persistResultsWithId(state, state.getAnalysisId(), "FAILED", e.getMessage());
                        Sinks.Many<WorkflowEvent> sink = analysisEventSinks.get(state.getAnalysisId());
                        if (sink != null) {
                            sink.tryEmitNext(WorkflowEvent.error("工作流执行失败: " + e.getMessage()));
                        }
                        removeEventSink(state.getAnalysisId());
                    } else {
                        persistResults(state, "FAILED", e.getMessage());
                    }
                });
    }

    /**
     * 使用预创建的分析记录执行工作流（独立分析模式，不关联对话）
     */
    public Flux<WorkflowEvent> executeWithAnalysisId(Long userId, Long analysisId, String query) {
        log.info("启动独立分析工作流: userId={}, analysisId={}, query={}", userId, analysisId, query);

        // 创建事件 Sink
        Sinks.Many<WorkflowEvent> sink = Sinks.many().replay().all();
        analysisEventSinks.put(analysisId, sink);

        // 更新分析记录状态为 RUNNING，使并发守卫生效
        analysisRepository.findById(analysisId).ifPresent(a -> {
            a.setWorkflowStatus("RUNNING");
            analysisRepository.save(a);
        });

        WorkflowState state = new WorkflowState();
        state.setUserId(userId);
        state.setAnalysisId(analysisId);
        state.setOriginalQuery(query);

        // 提取股票代码用于范围守卫
        var stockCodes = StockCodeExtractor.extract(query);
        if (!stockCodes.isEmpty()) {
            String code = stockCodes.iterator().next();
            state.setResolvedStockCode(code);
            state.setAllowedStockCodes(stockCodes);
            log.info("从查询中提取到数字代码: {}", code);
        } else {
            try {
                var resolved = StockResolver.resolve(query, httpClientService);
                state.setResolvedStockCode(resolved.code());
                state.setResolvedStockName(resolved.name());
                state.setAllowedStockCodes(java.util.Set.of(resolved.code()));
                log.info("标的解析成功: {}({})", resolved.name(), resolved.code());
            } catch (IllegalArgumentException e) {
                log.warn("标的解析失败: {}", e.getMessage());
                removeEventSink(analysisId);
                return Flux.just(WorkflowEvent.error("标的解析失败: " + e.getMessage()));
            }
        }

        // 解析用户级 ChatModel（Per-User API Key 路由）
        ChatModel userChatModel = null;
        try {
            userChatModel = userConfigService.getUserChatModel(userId);
            if (userChatModel != null) {
                log.info("[DeepAnalysis] 使用用户自定义配置, userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("[DeepAnalysis] 获取用户级 ChatModel 失败, 使用全局配置, userId={}: {}", userId, e.getMessage());
        }

        WorkflowGraph graph = buildGraph(userChatModel);

        return engine.execute(graph, state)
                .doOnNext(event -> {
                    Sinks.Many<WorkflowEvent> s = analysisEventSinks.get(analysisId);
                    if (s != null && s.currentSubscriberCount() > 0) {
                        s.tryEmitNext(event);
                    }
                })
                .doOnComplete(() -> {
                    persistResultsWithId(state, analysisId, "COMPLETED", null);
                    removeEventSink(analysisId);
                })
                .doOnError(e -> {
                    persistResultsWithId(state, analysisId, "FAILED", e.getMessage());
                    Sinks.Many<WorkflowEvent> s = analysisEventSinks.get(analysisId);
                    if (s != null) {
                        s.tryEmitNext(WorkflowEvent.error("工作流执行失败: " + e.getMessage()));
                    }
                    removeEventSink(analysisId);
                });
    }

    private WorkflowGraph buildGraph(ChatModel userChatModel) {
        // Layer 1: 4 个并行分析师
        ParallelFanOutNode layer1 = new ParallelFanOutNode("Layer1_DataCollection",
                List.of(
                        agentFactory.createAnalyst(AgentRole.MARKET_ANALYST, userChatModel),
                        agentFactory.createAnalyst(AgentRole.FUNDAMENTALS_ANALYST, userChatModel),
                        agentFactory.createAnalyst(AgentRole.NEWS_ANALYST, userChatModel),
                        agentFactory.createAnalyst(AgentRole.SOCIAL_ANALYST, userChatModel)
                ));

        // Layer 2: 多空辩论
        BullBearDebateOrchestrator layer2 = new BullBearDebateOrchestrator(
                agentFactory.createDebateNode(AgentRole.BULL_RESEARCHER, userChatModel),
                agentFactory.createDebateNode(AgentRole.BEAR_RESEARCHER, userChatModel),
                agentFactory.createJudgeNode(AgentRole.RESEARCH_MANAGER, userChatModel),
                properties.bullBearRounds());

        // Layer 3: 交易员
        TraderNode layer3 = agentFactory.createTraderNode(userChatModel);

        // Layer 4: 风险评估辩论
        RiskDebateOrchestrator layer4 = new RiskDebateOrchestrator(
                agentFactory.createDebateNode(AgentRole.AGGRESSIVE_ANALYST, userChatModel),
                agentFactory.createDebateNode(AgentRole.CONSERVATIVE_ANALYST, userChatModel),
                agentFactory.createDebateNode(AgentRole.NEUTRAL_ANALYST, userChatModel),
                agentFactory.createJudgeNode(AgentRole.RISK_JUDGE, userChatModel),
                properties.riskRounds());

        // Layer 5: 风险覆盖（可选）
        RiskOverrideNode layer5 = new RiskOverrideNode(riskOverrideProperties);

        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(layer1)
                .addNode(layer2)
                .addNode(layer3)
                .addNode(layer4)
                .addNode(layer5)
                .addEdge("Layer1_DataCollection", "BullBearDebate")
                .addEdge("BullBearDebate", "Trader")
                .addEdge("Trader", "RiskDebate")
                .addEdge("RiskDebate", "RiskOverride")
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

    private void persistResults(WorkflowState state, String status, String errorMessage) {
        try {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setUserId(state.getUserId());
            analysis.setConversationId(state.getConversationId());
            analysis.setOriginalQuery(state.getOriginalQuery());

            // 标的锁定信息
            analysis.setResolvedStockCode(state.getResolvedStockCode());
            analysis.setResolvedStockName(state.getResolvedStockName());

            // 工作流状态与时间
            analysis.setWorkflowStatus(status);
            analysis.setStartedAt(state.getStartTime() != null
                    ? java.time.LocalDateTime.ofInstant(state.getStartTime(), java.time.ZoneId.systemDefault())
                    : null);
            analysis.setCompletedAt(java.time.LocalDateTime.now());
            analysis.setErrorMessage(errorMessage);

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
            log.info("工作流结果已持久化，ID={}, status={}", analysis.getId(), status);
        } catch (JsonProcessingException e) {
            log.error("序列化工作流结果失败", e);
        } catch (Exception e) {
            log.error("持久化工作流结果失败", e);
        }
    }

    /**
     * 使用指定 analysisId 持久化结果（更新已有记录而非新建）
     */
    private void persistResultsWithId(WorkflowState state, Long analysisId, String status, String errorMessage) {
        // 已取消的分析不覆盖状态
        if (cancelledAnalyses.remove(analysisId)) {
            log.info("跳过已取消分析的结果持久化: analysisId={}", analysisId);
            return;
        }
        analysisRepository.findById(analysisId).ifPresent(analysis -> {
            try {
                analysis.setWorkflowStatus(status);
                analysis.setResolvedStockCode(state.getResolvedStockCode());
                analysis.setResolvedStockName(state.getResolvedStockName());

                // 分析师报告
                if (state.getAnalystReports() != null && !state.getAnalystReports().isEmpty()) {
                    Map<String, String> reportsMap = new HashMap<>();
                    state.getAnalystReports().forEach((k, v) -> reportsMap.put(k, v.reportContent()));
                    analysis.setAnalystReportsJson(objectMapper.writeValueAsString(reportsMap));
                }

                // 辩论记录
                if (state.getBullBearDebate() != null) {
                    analysis.setBullBearDebateJson(objectMapper.writeValueAsString(state.getBullBearDebate()));
                }
                analysis.setInvestmentPlan(state.getInvestmentPlan());
                analysis.setTradingProposal(state.getTradingProposal());
                if (state.getRiskDebate() != null) {
                    analysis.setRiskDebateJson(objectMapper.writeValueAsString(state.getRiskDebate()));
                }

                // 最终裁决
                if (state.getFinalDecision() != null) {
                    analysis.setAction(state.getFinalDecision().action());
                    analysis.setConfidence(state.getFinalDecision().confidence());
                    analysis.setTargetPrice(state.getFinalDecision().targetPrice());
                    analysis.setSummary(state.getFinalDecision().summary());
                }

                analysis.setCompletedAt(java.time.LocalDateTime.now());
                analysis.setErrorMessage(errorMessage);
                analysisRepository.save(analysis);
                log.info("分析结果已持久化: id={}, status={}", analysisId, status);
            } catch (JsonProcessingException e) {
                log.error("序列化工作流结果失败: analysisId={}", analysisId, e);
            } catch (Exception e) {
                log.error("持久化工作流结果失败: analysisId={}", analysisId, e);
            }
        });
    }
}
