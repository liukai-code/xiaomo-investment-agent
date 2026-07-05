package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.workflow.config.RiskOverrideProperties;
import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.FinalDecision;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;

@Slf4j
public class RiskOverrideNode implements WorkflowNode {

    private final RiskOverrideProperties properties;

    public RiskOverrideNode(RiskOverrideProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "RiskOverride";
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        FinalDecision original = state.getFinalDecision();
        if (original == null) {
            log.warn("无最终裁决可覆盖");
            return Flux.empty();
        }

        if (!properties.enabled()) {
            log.info("风险覆盖已禁用，跳过");
            return Flux.empty();
        }

        String action = original.action();
        double confidence = original.confidence();
        double targetPrice = original.targetPrice();
        String summary = original.summary();
        StringBuilder reason = new StringBuilder();

        // 规则1: BUY 信号置信度低于阈值，降为 HOLD
        if ("BUY".equals(action) && confidence < properties.vetoBuyThreshold()) {
            action = "HOLD";
            reason.append("风险覆盖: BUY 置信度 ").append(String.format("%.2f", confidence))
                    .append(" 低于阈值 ").append(properties.vetoBuyThreshold()).append("，降为 HOLD");
            log.info(reason.toString());
        }

        // 规则2: 降级一级（降低置信度 10%）
        if (properties.downgradeOne() && reason.isEmpty()) {
            confidence = Math.max(0, confidence - 0.1);
            reason.append("风险覆盖: 置信度降级 10%");
            log.info(reason.toString());
        }

        // 规则3: 降级两级（降低置信度 20%）
        if (properties.downgradeTwo() && reason.isEmpty()) {
            confidence = Math.max(0, confidence - 0.2);
            reason.append("风险覆盖: 置信度降级 20%");
            log.info(reason.toString());
        }

        if (reason.isEmpty()) {
            log.info("风险覆盖规则未触发，保持原裁决");
            return Flux.empty();
        }

        // 应用覆盖
        FinalDecision overridden = new FinalDecision(
                action, confidence, targetPrice,
                summary + "\n\n[风险覆盖] " + reason,
                Instant.now()
        );
        state.setFinalDecision(overridden);
        sink.tryEmitNext(WorkflowEvent.riskOverride(action, reason.toString()));
        log.info("风险覆盖完成: action={}, confidence={}", action, confidence);

        return Flux.empty();
    }
}
