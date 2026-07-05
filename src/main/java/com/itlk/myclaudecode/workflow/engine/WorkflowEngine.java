package com.itlk.myclaudecode.workflow.engine;

import com.itlk.myclaudecode.workflow.config.WorkflowProperties;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class WorkflowEngine {

    private final WorkflowProperties properties;

    public WorkflowEngine(WorkflowProperties properties) {
        this.properties = properties;
    }

    public Flux<WorkflowEvent> execute(WorkflowGraph graph, WorkflowState state) {
        Sinks.Many<WorkflowEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        state.setEventSink(sink);

        Instant pipelineStart = Instant.now();
        Duration totalBudget = Duration.ofSeconds(properties.timeoutSeconds());
        int stageMinSeconds = properties.stageMinSeconds();

        return Flux.fromIterable(graph.resolveExecutionPath(state))
                .concatMap(node -> {
                    Duration elapsed = Duration.between(pipelineStart, Instant.now());
                    Duration remaining = totalBudget.minus(elapsed);

                    if (remaining.toSeconds() < stageMinSeconds) {
                        log.warn("工作流时间预算不足，跳过节点: {} (剩余{}s)", node.name(), remaining.toSeconds());
                        sink.tryEmitNext(WorkflowEvent.phaseSkipped(node.name(), "时间预算不足"));
                        return Flux.empty();
                    }

                    log.info("工作流进入节点: {} (剩余预算{}s)", node.name(), remaining.toSeconds());
                    state.setCurrentPhase(node.name());
                    sink.tryEmitNext(WorkflowEvent.phaseStart(node.name()));

                    return node.execute(state, sink)
                            .timeout(remaining)
                            .doOnComplete(() -> sink.tryEmitNext(WorkflowEvent.phaseComplete(node.name())))
                            .onErrorResume(e -> {
                                Throwable cause = e.getCause() != null ? e.getCause() : e;
                                if (cause instanceof java.util.concurrent.TimeoutException) {
                                    log.warn("节点 {} 执行超时", node.name());
                                    sink.tryEmitNext(WorkflowEvent.error("节点 " + node.name() + " 超时，已跳过"));
                                    return Flux.empty();
                                }
                                return Flux.error(e);
                            });
                })
                .doOnComplete(() -> {
                    log.info("工作流执行完成，总耗时: {}s", Duration.between(pipelineStart, Instant.now()).toSeconds());
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
