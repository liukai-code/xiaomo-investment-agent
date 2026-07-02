package com.itlk.myclaudecode.workflow.engine;

import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
public class WorkflowEngine {

    public Flux<WorkflowEvent> execute(WorkflowGraph graph, WorkflowState state) {
        Sinks.Many<WorkflowEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        state.setEventSink(sink);

        return Flux.fromIterable(graph.resolveExecutionPath(state))
                .concatMap(node -> {
                    log.info("工作流进入节点: {}", node.name());
                    state.setCurrentPhase(node.name());
                    sink.tryEmitNext(WorkflowEvent.phaseStart(node.name()));
                    return node.execute(state, sink)
                            .doOnComplete(() -> sink.tryEmitNext(WorkflowEvent.phaseComplete(node.name())));
                })
                .doOnComplete(() -> {
                    log.info("工作流执行完成");
                    sink.tryEmitComplete();
                })
                .mergeWith(sink.asFlux())
                .onErrorResume(e -> {
                    log.error("工作流执行错误: {}", e.getMessage(), e);
                    sink.tryEmitNext(WorkflowEvent.error(e.getMessage()));
                    sink.tryEmitComplete();
                    return Flux.empty();
                });
    }
}
