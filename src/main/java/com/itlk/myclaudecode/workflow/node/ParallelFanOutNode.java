package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
public class ParallelFanOutNode implements WorkflowNode {

    private final String name;
    private final List<WorkflowNode> parallelNodes;

    public ParallelFanOutNode(String name, List<WorkflowNode> parallelNodes) {
        this.name = name;
        this.parallelNodes = parallelNodes;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("[{}] 开始并行执行 {} 个节点", name, parallelNodes.size());

        List<Flux<WorkflowEvent>> streams = parallelNodes.stream()
                .map(node -> node.execute(state, sink))
                .toList();

        // Flux.merge 并行执行所有节点，等待全部完成
        return Flux.merge(parallelNodes.size(), streams.toArray(new Flux[0]))
                .doOnComplete(() -> {
                    sink.tryEmitNext(WorkflowEvent.phaseComplete(name));
                    log.info("[{}] 所有并行节点执行完成", name);
                });
    }
}
