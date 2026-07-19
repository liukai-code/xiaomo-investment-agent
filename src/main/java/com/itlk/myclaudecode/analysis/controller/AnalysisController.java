package com.itlk.myclaudecode.analysis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.analysis.service.AnalysisService;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.state.FinalDecision;
import com.itlk.myclaudecode.workflow.service.DeepAnalysisWorkflow;
import com.itlk.myclaudecode.workflow.util.StockResolver;
import com.itlk.myclaudecode.common.config.HttpClientService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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
    @Resource
    private HttpClientService httpClientService;

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
        String stockCode;
        String stockName;
        try {
            var resolved = StockResolver.resolve(query, httpClientService);
            stockCode = resolved.code();
            stockName = resolved.name();
        } catch (Exception e) {
            log.warn("标的解析失败: {}", e.getMessage());
            return Result.error("标的解析失败: " + e.getMessage());
        }

        // 创建分析记录
        WorkflowAnalysis analysis = analysisService.createAnalysis(userId, query, stockCode, stockName);

        // 异步启动工作流（不阻塞响应）
        Long analysisId = analysis.getId();
        try {
            deepAnalysisWorkflow.executeWithAnalysisId(userId, analysisId, query);
        } catch (Exception e) {
            log.error("启动分析失败", e);
        }

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
                // 分析已完成，从数据库重建事件流
                return buildEventsFromAnalysis(id, userId);
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
     * 从已完成的分析记录重建事件流
     */
    private Flux<ServerSentEvent<String>> buildEventsFromAnalysis(Long analysisId, Long userId) {
        WorkflowAnalysis analysis = analysisService.getAnalysis(analysisId, userId);
        if (analysis == null || analysis.getWorkflowStatus() == null) {
            return Flux.just(doneEvent(analysisId));
        }

        List<WorkflowEvent> events = new java.util.ArrayList<>();

        // Layer 1: 分析师报告
        events.add(WorkflowEvent.phaseStart("Layer1_DataCollection"));
        if (analysis.getAnalystReportsJson() != null) {
            try {
                Map<String, String> reports = objectMapper.readValue(
                        analysis.getAnalystReportsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                reports.forEach((name, content) -> {
                    events.add(WorkflowEvent.agentStart(name));
                    events.add(WorkflowEvent.agentComplete(name, content));
                });
            } catch (Exception ignored) {}
        }
        events.add(WorkflowEvent.phaseComplete("Layer1_DataCollection"));

        // Layer 2: 多空辩论
        events.add(WorkflowEvent.phaseStart("BullBearDebate"));
        if (analysis.getBullBearDebateJson() != null) {
            try {
                List<Map<String, Object>> debate = objectMapper.readValue(
                        analysis.getBullBearDebateJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                int round = 1;
                for (Map<String, Object> msg : debate) {
                    String speaker = (String) msg.get("speakerName");
                    String argument = (String) msg.get("argument");
                    if (speaker != null) {
                        events.add(WorkflowEvent.debateStart(speaker, round));
                        events.add(WorkflowEvent.debateComplete(speaker, argument));
                        round++;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (analysis.getInvestmentPlan() != null) {
            events.add(WorkflowEvent.agentStart("ResearchManager"));
            events.add(WorkflowEvent.agentComplete("ResearchManager", analysis.getInvestmentPlan()));
        }
        events.add(WorkflowEvent.phaseComplete("BullBearDebate"));

        // Layer 3: 交易决策
        events.add(WorkflowEvent.phaseStart("Trader"));
        if (analysis.getTradingProposal() != null) {
            events.add(WorkflowEvent.agentStart("Trader"));
            events.add(WorkflowEvent.agentComplete("Trader", analysis.getTradingProposal()));
        }
        events.add(WorkflowEvent.phaseComplete("Trader"));

        // Layer 4: 风险评估
        events.add(WorkflowEvent.phaseStart("RiskDebate"));
        if (analysis.getRiskDebateJson() != null) {
            try {
                List<Map<String, Object>> debate = objectMapper.readValue(
                        analysis.getRiskDebateJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                int round = 1;
                for (Map<String, Object> msg : debate) {
                    String speaker = (String) msg.get("speakerName");
                    String argument = (String) msg.get("argument");
                    if (speaker != null) {
                        events.add(WorkflowEvent.debateStart(speaker, round));
                        events.add(WorkflowEvent.debateComplete(speaker, argument));
                        round++;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (analysis.getAction() != null) {
            FinalDecision decision = new FinalDecision(
                    analysis.getAction(),
                    analysis.getConfidence() != null ? analysis.getConfidence() : 0.0,
                    analysis.getTargetPrice() != null ? analysis.getTargetPrice() : 0.0,
                    analysis.getSummary(),
                    null);
            events.add(WorkflowEvent.agentStart("RiskJudge"));
            events.add(WorkflowEvent.agentComplete("RiskJudge", analysis.getSummary()));
            events.add(WorkflowEvent.finalDecision(decision));
        }
        events.add(WorkflowEvent.phaseComplete("RiskDebate"));

        Flux<ServerSentEvent<String>> eventFlux = Flux.fromIterable(events)
                .map(event -> {
                    try {
                        return ServerSentEvent.<String>builder()
                                .event("workflow")
                                .data(objectMapper.writeValueAsString(event))
                                .build();
                    } catch (Exception e) {
                        return doneEvent(analysisId);
                    }
                });

        return eventFlux.concatWith(Flux.just(doneEvent(analysisId)));
    }

    private ServerSentEvent<String> doneEvent(Long analysisId) {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("{\"analysisId\":" + analysisId + "}")
                .build();
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

    /**
     * 取消正在运行的分析
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelAnalysis(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        // 验证权限
        analysisService.getAnalysis(id, userId);
        deepAnalysisWorkflow.cancelAnalysis(id);
        return Result.success();
    }
}
