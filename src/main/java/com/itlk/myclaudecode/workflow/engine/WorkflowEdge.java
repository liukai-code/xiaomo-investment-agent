package com.itlk.myclaudecode.workflow.engine;

import com.itlk.myclaudecode.workflow.state.WorkflowState;

import java.util.function.Predicate;

public record WorkflowEdge(String fromNode, String toNode, Predicate<WorkflowState> condition) {}
