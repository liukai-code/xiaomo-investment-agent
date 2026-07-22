package com.xiaomo.agent.workflow.event;

public enum WorkflowEventType {
    AGENT_START,
    AGENT_CHUNK,
    AGENT_COMPLETE,
    DEBATE_START,
    DEBATE_CHUNK,
    DEBATE_COMPLETE,
    PHASE_START,
    PHASE_COMPLETE,
    PHASE_SKIPPED,
    FINAL_DECISION,
    RISK_OVERRIDE,
    ERROR
}
