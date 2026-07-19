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
import com.itlk.myclaudecode.workflow.util.StockResolver;
import com.itlk.myclaudecode.common.config.HttpClientService;
import com.itlk.myclaudecode.user.config.UserConfigDTO;
import com.itlk.myclaudecode.user.service.FreeQuotaService;
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
    private final FreeQuotaService freeQuotaService;

    /** 独立分析模式的事件 Sink 映射（analysisId -> Sink） */
    private final ConcurrentHashMap<Long, Sinks.Many<WorkflowEvent>> analysisEventSinks = new ConcurrentHashMap<>();

    /** 已取消的分析 ID 集合（防止工作流完成后覆盖状态） */
    private final java.util.Set<Long> cancelledAnalyses = ConcurrentHashMap.newKeySet();

    /** 正在运行的工作流 Disposable 映射（analysisId -> Disposable，取消时 dispose 用） */
    private final ConcurrentHashMap<Long, reactor.core.Disposable> runningFluxes = new ConcurrentHashMap<>();

    /** 正在运行的工作流状态映射（analysisId -> WorkflowState，取消时触发 cancelSink 用） */
    private final ConcurrentHashMap<Long, com.itlk.myclaudecode.workflow.state.WorkflowState> runningStates = new ConcurrentHashMap<>();

    public DeepAnalysisWorkflow(WorkflowAgentFactory agentFactory,
                                 WorkflowEngine engine,
                                 WorkflowAnalysisRepository analysisRepository,
                                 WorkflowProperties properties,
                                 RiskOverrideProperties riskOverrideProperties,
                                 ObjectMapper objectMapper,
                                 HttpClientService httpClientService,
                                 UserConfigService userConfigService,
                                 FreeQuotaService freeQuotaService) {
        this.agentFactory = agentFactory;
        this.engine = engine;
        this.analysisRepository = analysisRepository;
        this.properties = properties;
        this.riskOverrideProperties = riskOverrideProperties;
        this.objectMapper = objectMapper;
        this.httpClientService = httpClientService;
        this.userConfigService = userConfigService;
        this.freeQuotaService = freeQuotaService;
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

        // 触发 cancel sink，让引擎的 takeUntilOther 立即终止
        com.itlk.myclaudecode.workflow.state.WorkflowState state = runningStates.get(analysisId);
        if (state != null) {
            state.signalCancel();
            // 取消时也要扣减已消耗的免费额度
            if (state.isUseFreeQuota()) {
                long consumed = state.getTotalEstimatedTokens().get();
                if (consumed > 0) {
                    freeQuotaService.deduct(state.getUserId(), consumed);
                    log.info("[DeepAnalysis] 取消时扣减免费额度: userId={}, consumed={}", state.getUserId(), consumed);
                }
            }
        }

        // dispose 正在运行的 Flux 订阅
        reactor.core.Disposable disposable = runningFluxes.remove(analysisId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.info("已 dispose 工作流 Flux: analysisId={}", analysisId);
        }

        removeEventSink(analysisId);
        analysisRepository.findById(analysisId).ifPresent(a -> {
            a.setWorkflowStatus("CANCELLED");
            a.setErrorMessage("用户手动停止");
            a.setCompletedAt(java.time.LocalDateTime.now());
            analysisRepository.save(a);
        });
        runningStates.remove(analysisId);
        log.info("分析已取消: analysisId={}", analysisId);
    }

    /**
     * 执行深度分析工作流（对话模式，关联 conversationId）
     */
    public Flux<WorkflowEvent> execute(Long userId, Long conversationId, String query) {
        log.info("启动深度分析工作流: userId={}, query={}", userId, query);

        // 免费额度检查
        UserConfigDTO userConfig = userConfigService.getConfig(userId);
        boolean hasOwnApiKey = userConfig != null && userConfig.getApiKey() != null && !userConfig.getApiKey().isEmpty();
        if (!hasOwnApiKey) {
            long remaining = freeQuotaService.getRemainingQuota(userId);
            if (remaining <= 0) {
                return Flux.just(WorkflowEvent.error("免费体验额度已用完，请在设置中配置自己的 API Key 继续使用"));
            }
        }

        WorkflowState state = new WorkflowState();
        state.setUserId(userId);
        state.setConversationId(conversationId);
        state.setOriginalQuery(query);
        state.setUseFreeQuota(!hasOwnApiKey);

        // 解析标的（统一走 StockResolver 验证）
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

        // 注入取消检查器（analysisId 可能后续才设置，用延迟检查）
        state.setCancelledChecker(() -> {
            Long aid = state.getAnalysisId();
            return aid != null && cancelledAnalyses.contains(aid);
        });

        return engine.execute(graph, state)
                .doOnNext(event -> {
                    // 如果存在独立分析的 Sink，同步发布事件
                    Long analysisId = state.getAnalysisId();
                    if (analysisId != null) {
                        Sinks.Many<WorkflowEvent> sink = analysisEventSinks.get(analysisId);
                        if (sink != null) {
                            sink.tryEmitNext(event);
                        }
                    }
                })
                .doOnComplete(() -> {
                    // 免费额度扣减
                    if (!hasOwnApiKey) {
                        long consumed = state.getTotalEstimatedTokens().get();
                        if (consumed > 0) {
                            freeQuotaService.deduct(userId, consumed);
                            log.info("[DeepAnalysis] 免费额度扣减: userId={}, consumed={}", userId, consumed);
                        }
                    }
                    Long aid = state.getAnalysisId();
                    if (aid != null && cancelledAnalyses.contains(aid)) {
                        log.info("工作流已被取消，不覆盖状态: analysisId={}", aid);
                        removeEventSink(aid);
                    } else {
                        log.info("工作流完成，持久化结果");
                        if (aid != null) {
                            persistResultsWithId(state, aid, "COMPLETED", null);
                            removeEventSink(aid);
                        } else {
                            persistResults(state, "COMPLETED", null);
                        }
                    }
                })
                .doOnError(e -> {
                    log.error("工作流执行错误: {}", e.getMessage(), e);
                    Long aid = state.getAnalysisId();
                    if (aid != null && cancelledAnalyses.contains(aid)) {
                        log.info("已取消的工作流错误，忽略: analysisId={}, error={}", aid, e.getMessage());
                        removeEventSink(aid);
                    } else if (aid != null) {
                        persistResultsWithId(state, aid, "FAILED", e.getMessage());
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
    public void executeWithAnalysisId(Long userId, Long analysisId, String query) {
        log.info("启动独立分析工作流: userId={}, analysisId={}, query={}", userId, analysisId, query);

        // 免费额度检查
        UserConfigDTO userConfig = userConfigService.getConfig(userId);
        boolean hasOwnApiKey = userConfig != null && userConfig.getApiKey() != null && !userConfig.getApiKey().isEmpty();
        if (!hasOwnApiKey) {
            long remaining = freeQuotaService.getRemainingQuota(userId);
            if (remaining <= 0) {
                log.warn("[DeepAnalysis] 免费额度不足, userId={}", userId);
                Sinks.Many<WorkflowEvent> errorSink = Sinks.many().replay().all();
                errorSink.tryEmitNext(WorkflowEvent.error("免费体验额度已用完，请在设置中配置自己的 API Key 继续使用"));
                errorSink.tryEmitComplete();
                analysisEventSinks.put(analysisId, errorSink);
                return;
            }
        }

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
        state.setUseFreeQuota(!hasOwnApiKey);

        // 解析标的（统一走 StockResolver 验证）
        try {
            var resolved = StockResolver.resolve(query, httpClientService);
            state.setResolvedStockCode(resolved.code());
            state.setResolvedStockName(resolved.name());
            state.setAllowedStockCodes(java.util.Set.of(resolved.code()));
            log.info("标的解析成功: {}({})", resolved.name(), resolved.code());
        } catch (IllegalArgumentException e) {
            log.warn("标的解析失败: {}", e.getMessage());
            sink.tryEmitNext(WorkflowEvent.error("标的解析失败: " + e.getMessage()));
            sink.tryEmitComplete();
            removeEventSink(analysisId);
            return;
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

        // 注入取消检查器
        state.setCancelledChecker(() -> cancelledAnalyses.contains(analysisId));

        // 保存 state 引用，供取消时触发 cancelSink
        runningStates.put(analysisId, state);

        // 内部订阅并保存 Disposable，供取消时 dispose
        reactor.core.Disposable disposable = engine.execute(graph, state)
                .doOnNext(event -> {
                    Sinks.Many<WorkflowEvent> s = analysisEventSinks.get(analysisId);
                    if (s != null) {
                        s.tryEmitNext(event);
                    }
                })
                .doOnComplete(() -> {
                    // 免费额度扣减
                    if (!hasOwnApiKey) {
                        long consumed = state.getTotalEstimatedTokens().get();
                        if (consumed > 0) {
                            freeQuotaService.deduct(userId, consumed);
                            log.info("[DeepAnalysis] 免费额度扣减: userId={}, consumed={}", userId, consumed);
                        }
                    }
                    runningFluxes.remove(analysisId);
                    runningStates.remove(analysisId);
                    if (cancelledAnalyses.contains(analysisId)) {
                        log.info("工作流已被取消，不覆盖状态: analysisId={}", analysisId);
                        removeEventSink(analysisId);
                    } else {
                        persistResultsWithId(state, analysisId, "COMPLETED", null);
                        removeEventSink(analysisId);
                    }
                })
                .doOnError(e -> {
                    runningFluxes.remove(analysisId);
                    runningStates.remove(analysisId);
                    if (cancelledAnalyses.contains(analysisId)) {
                        log.info("已取消的工作流错误，忽略: analysisId={}, error={}", analysisId, e.getMessage());
                        removeEventSink(analysisId);
                    } else {
                        persistResultsWithId(state, analysisId, "FAILED", e.getMessage());
                        Sinks.Many<WorkflowEvent> s = analysisEventSinks.get(analysisId);
                        if (s != null) {
                            s.tryEmitNext(WorkflowEvent.error("工作流执行失败: " + e.getMessage()));
                        }
                        removeEventSink(analysisId);
                    }
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();
        runningFluxes.put(analysisId, disposable);
    }

    private WorkflowGraph buildGraph(ChatModel userChatModel) {
        // Layer 1: 3 个并行分析师
        ParallelFanOutNode layer1 = new ParallelFanOutNode("Layer1_DataCollection",
                List.of(
                        agentFactory.createAnalyst(AgentRole.MARKET_ANALYST, userChatModel),
                        agentFactory.createAnalyst(AgentRole.FUNDAMENTALS_ANALYST, userChatModel),
                        agentFactory.createAnalyst(AgentRole.NEWS_ANALYST, userChatModel)
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
