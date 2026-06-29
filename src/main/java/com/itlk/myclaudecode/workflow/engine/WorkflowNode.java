package com.itlk.myclaudecode.workflow.engine;

import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public interface WorkflowNode {
    String name();
    Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink);
}
