package com.itlk.myclaudecode.workflow.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.itlk.myclaudecode.workflow.state.FinalDecision;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowEvent(
        WorkflowEventType type,
        String agentName,
        String content,
        String phase,
        Instant timestamp
) {
    public static WorkflowEvent agentStart(String agent) {
        return new WorkflowEvent(WorkflowEventType.AGENT_START, agent, null, null, Instant.now());
    }

    public static WorkflowEvent agentChunk(String agent, String chunk) {
        return new WorkflowEvent(WorkflowEventType.AGENT_CHUNK, agent, chunk, null, Instant.now());
    }

    public static WorkflowEvent agentComplete(String agent, String report) {
        return new WorkflowEvent(WorkflowEventType.AGENT_COMPLETE, agent, report, null, Instant.now());
    }

    public static WorkflowEvent debateStart(String agent, int round) {
        return new WorkflowEvent(WorkflowEventType.DEBATE_START, agent, "round:" + round, null, Instant.now());
    }

    public static WorkflowEvent debateChunk(String agent, String chunk) {
        return new WorkflowEvent(WorkflowEventType.DEBATE_CHUNK, agent, chunk, null, Instant.now());
    }

    public static WorkflowEvent debateComplete(String agent, String argument) {
        return new WorkflowEvent(WorkflowEventType.DEBATE_COMPLETE, agent, argument, null, Instant.now());
    }

    public static WorkflowEvent phaseStart(String phase) {
        return new WorkflowEvent(WorkflowEventType.PHASE_START, null, null, phase, Instant.now());
    }

    public static WorkflowEvent phaseComplete(String phase) {
        return new WorkflowEvent(WorkflowEventType.PHASE_COMPLETE, null, null, phase, Instant.now());
    }

    public static WorkflowEvent phaseSkipped(String phase, String reason) {
        return new WorkflowEvent(WorkflowEventType.PHASE_SKIPPED, null, reason, phase, Instant.now());
    }

    public static WorkflowEvent finalDecision(FinalDecision decision) {
        return new WorkflowEvent(WorkflowEventType.FINAL_DECISION, "RiskJudge",
                decision.summary(), "layer4", Instant.now());
    }

    public static WorkflowEvent error(String message) {
        return new WorkflowEvent(WorkflowEventType.ERROR, null, message, null, Instant.now());
    }

    public static WorkflowEvent riskOverride(String action, String reason) {
        return new WorkflowEvent(WorkflowEventType.RISK_OVERRIDE, "RiskOverride", reason, "RiskOverride", Instant.now());
    }
}
