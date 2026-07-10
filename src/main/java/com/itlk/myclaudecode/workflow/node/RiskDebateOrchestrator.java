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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RiskDebateOrchestrator implements WorkflowNode {

    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*(\\{[\\s\\S]*?})\\s*```|\\{[\\s\\S]*?}");
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
                                    // 用原始结果（含 JSON）解析决策，event.content() 已剥离 JSON
                                    FinalDecision decision = parseDecision(riskJudge.getLastRawResult());
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
                // group(1) = code block 内的 JSON，group(0) = 整个匹配（可能含 ```）
                String json = matcher.group(1) != null ? matcher.group(1) : matcher.group(0);
                JsonNode node = objectMapper.readTree(json);

                String action = node.has("action") ? node.get("action").asText("HOLD") : "HOLD";
                double confidence = node.has("confidence") ? node.get("confidence").asDouble(0.5) : 0.5;
                double targetPrice = node.has("target_price") ? node.get("target_price").asDouble(0.0) : 0.0;
                String summary = node.has("summary") ? node.get("summary").asText("") : "";

                if (summary.isEmpty()) {
                    // 尝试从已知字段提取可读摘要，避免直接使用原始 JSON
                    if (node.has("key_argument")) {
                        summary = node.get("key_argument").asText("");
                    }
                    if (summary.isEmpty() && node.has("position_stance")) {
                        summary = "仓位建议: " + node.get("recommended_position_pct").asInt(0)
                                + "%, 立场: " + node.get("position_stance").asText("N/A");
                    }
                    if (summary.isEmpty()) {
                        summary = content.substring(0, Math.min(content.length(), 200));
                    }
                }

                return new FinalDecision(action, confidence, targetPrice, summary, Instant.now());
            }
        } catch (Exception e) {
            log.warn("解析最终裁决 JSON 失败，使用默认值: {}", e.getMessage());
        }

        // fallback: 从文本中推断
        String upper = content.toUpperCase();
        String action = "HOLD";
        if (upper.contains("BUY") || upper.contains("买入")) {
            action = "BUY";
        } else if (upper.contains("SELL") || upper.contains("卖出")) {
            action = "SELL";
        }

        // 剥离 JSON 代码块，只保留自然语言部分作为摘要
        String textOnly = content.replaceAll("```json\\s*[\\s\\S]*?```\\s*", "")
                .replaceAll("\\{[\\s\\S]*?}", "").trim();
        String fallbackSummary = textOnly.isEmpty()
                ? "裁决结果: " + action
                : textOnly.substring(0, Math.min(textOnly.length(), 200));

        return new FinalDecision(action, 0.5, 0.0, fallbackSummary, Instant.now());
    }
}
