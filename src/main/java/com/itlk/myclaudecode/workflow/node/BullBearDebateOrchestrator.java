package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.event.WorkflowEventType;
import com.itlk.myclaudecode.workflow.state.DebateMessage;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class BullBearDebateOrchestrator implements WorkflowNode {

    private final DebateNode bull;
    private final DebateNode bear;
    private final JudgeNode judge;
    private final int rounds;

    public BullBearDebateOrchestrator(DebateNode bull, DebateNode bear,
                                       JudgeNode judge, int rounds) {
        this.bull = bull;
        this.bear = bear;
        this.judge = judge;
        this.rounds = rounds;
    }

    @Override
    public String name() {
        return "BullBearDebate";
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("开始多空辩论，共 {} 轮", rounds);
        List<DebateMessage> debateHistory = new CopyOnWriteArrayList<>();

        // 逐轮交替辩论
        Flux<WorkflowEvent> debateChain = Flux.empty();
        for (int i = 0; i < rounds; i++) {
            debateChain = debateChain
                    .concatWith(bull.debateRound(state, debateHistory, sink))
                    .concatWith(bear.debateRound(state, debateHistory, sink));
        }

        // 辩论结束后，裁决者做出判断
        return debateChain
                .concatWith(Flux.defer(() -> {
                    state.getBullBearDebate().addAll(debateHistory);
                    return judge.makeJudgment(state, debateHistory, sink)
                            .doOnNext(event -> {
                                if (event.type() == WorkflowEventType.AGENT_COMPLETE) {
                                    state.setInvestmentPlan(event.content());
                                    log.info("投资计划已生成");
                                }
                            });
                }))
                .doOnComplete(() -> {
                    sink.tryEmitNext(WorkflowEvent.phaseComplete("BullBearDebate"));
                    log.info("多空辩论完成");
                });
    }
}
