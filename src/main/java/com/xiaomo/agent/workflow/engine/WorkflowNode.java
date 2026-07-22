package com.xiaomo.agent.workflow.engine;

import com.xiaomo.agent.workflow.event.WorkflowEvent;
import com.xiaomo.agent.workflow.state.WorkflowState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public interface WorkflowNode {
    String name();
    Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink);
}
