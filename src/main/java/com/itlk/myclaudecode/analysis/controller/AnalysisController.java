package com.itlk.myclaudecode.analysis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.analysis.service.AnalysisService;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.service.DeepAnalysisWorkflow;
import com.itlk.myclaudecode.workflow.util.StockCodeExtractor;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
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
        var stockCodes = StockCodeExtractor.extract(query);
        String stockCode = stockCodes.isEmpty() ? null : stockCodes.iterator().next();
        String stockName = null;

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
