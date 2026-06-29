package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.event.WorkflowEventType;
import com.itlk.myclaudecode.workflow.state.DebateMessage;
import com.itlk.myclaudecode.workflow.state.FinalDecision;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RiskDebateOrchestrator implements WorkflowNode {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^}]+}");

    private final DebateNode aggressive;
    private final DebateNode conservative;
    private final DebateNode neutral;
    private final JudgeNode riskJudge;
    private final int rounds;

    public RiskDebateOrchestrator(DebateNode aggressive, DebateNode conservative,
                                   DebateNode neutral, JudgeNode riskJudge, int rounds) {
        this.aggressive = aggressive;
        this.conservative = conservative;
        this.neutral = neutral;
        this.riskJudge = riskJudge;
        this.rounds = rounds;
    }

    @Override
    public String name() {
        return "RiskDebate";
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("开始风险评估辩论，共 {} 轮", rounds);
        List<DebateMessage> riskDebateHistory = new CopyOnWriteArrayList<>();

        // 逐轮三方辩论
        @SuppressWarnings("unchecked")
        Flux<WorkflowEvent>[] roundFluxes = new Flux[rounds * 3];
        for (int i = 0; i < rounds; i++) {
            roundFluxes[i * 3] = Flux.defer(() -> aggressive.debateRound(state, riskDebateHistory, sink));
            roundFluxes[i * 3 + 1] = Flux.defer(() -> conservative.debateRound(state, riskDebateHistory, sink));
            roundFluxes[i * 3 + 2] = Flux.defer(() -> neutral.debateRound(state, riskDebateHistory, sink));
        }
        Flux<WorkflowEvent> debateChain = Flux.concat(roundFluxes);

        // 辩论结束后，风险裁决官做出最终裁决
        return debateChain
                .concatWith(Flux.defer(() -> {
                    state.getRiskDebate().addAll(riskDebateHistory);
                    return riskJudge.makeJudgment(state, riskDebateHistory, sink)
                            .doOnNext(event -> {
                                if (event.type() == WorkflowEventType.AGENT_COMPLETE) {
                                    FinalDecision decision = parseDecision(event.content());
                                    state.setFinalDecision(decision);
                                    sink.tryEmitNext(WorkflowEvent.finalDecision(decision));
                                    log.info("最终裁决: action={}, confidence={}",
                                            decision.action(), decision.confidence());
                                }
                            });
                }))
                .doOnComplete(() -> {
                    sink.tryEmitNext(WorkflowEvent.phaseComplete("RiskDebate"));
                    log.info("风险评估辩论完成");
                });
    }

    private FinalDecision parseDecision(String content) {
        try {
            Matcher matcher = JSON_PATTERN.matcher(content);
            if (matcher.find()) {
                String json = matcher.group();
                // 简单解析 JSON 字段
                String action = extractJsonValue(json, "action");
                String confidenceStr = extractJsonValue(json, "confidence");
                String targetPriceStr = extractJsonValue(json, "target_price");
                String summary = extractJsonValue(json, "summary");

                double confidence = confidenceStr != null ? Double.parseDouble(confidenceStr) : 0.5;
                double targetPrice = targetPriceStr != null ? Double.parseDouble(targetPriceStr) : 0.0;

                return new FinalDecision(
                        action != null ? action : "HOLD",
                        confidence,
                        targetPrice,
                        summary != null ? summary : content.substring(0, Math.min(content.length(), 200)),
                        Instant.now()
                );
            }
        } catch (Exception e) {
            log.warn("解析最终裁决 JSON 失败，使用默认值: {}", e.getMessage());
        }

        // fallback: 从文本中推断
        String upper = content.toUpperCase();
        String action = "HOLD";
        if (upper.contains("BUY") || upper.contains("买入") || upper.contains("买入")) {
            action = "BUY";
        } else if (upper.contains("SELL") || upper.contains("卖出") || upper.contains("卖出")) {
            action = "SELL";
        }

        return new FinalDecision(action, 0.5, 0.0,
                content.substring(0, Math.min(content.length(), 200)), Instant.now());
    }

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }
}
