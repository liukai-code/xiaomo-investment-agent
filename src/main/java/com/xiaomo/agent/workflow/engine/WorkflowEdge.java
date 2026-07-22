package com.xiaomo.agent.workflow.engine;

import com.xiaomo.agent.workflow.state.WorkflowState;

import java.util.function.Predicate;

public record WorkflowEdge(String fromNode, String toNode, Predicate<WorkflowState> condition) {}
