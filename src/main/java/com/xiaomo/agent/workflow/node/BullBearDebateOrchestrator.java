package com.xiaomo.agent.workflow.node;

import com.xiaomo.agent.workflow.engine.WorkflowNode;
import com.xiaomo.agent.workflow.event.WorkflowEvent;
import com.xiaomo.agent.workflow.state.DebateMessage;
import com.xiaomo.agent.workflow.state.WorkflowState;
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

        // 用 Flux.defer 包装每一轮，确保按序执行
        Flux<WorkflowEvent> chain = Flux.defer(() -> {
            // 构建逐轮辩论链
            @SuppressWarnings("unchecked")
            Flux<WorkflowEvent>[] roundFluxes = new Flux[rounds * 2];
            for (int i = 0; i < rounds; i++) {
                roundFluxes[i * 2] = Flux.defer(() -> bull.debateRound(state, debateHistory, sink));
                roundFluxes[i * 2 + 1] = Flux.defer(() -> bear.debateRound(state, debateHistory, sink));
            }
            return Flux.concat(roundFluxes);
        });

        // 辩论结束后，裁决者做出判断
        return chain
                .concatWith(Flux.defer(() -> {
                    state.getBullBearDebate().addAll(debateHistory);
                    log.info("辩论记录已写入状态，共 {} 条", debateHistory.size());
                    return judge.makeJudgment(state, debateHistory, sink)
                            .doOnComplete(() -> {
                                // AGENT_COMPLETE 通过 sink 副作用发出，不经过 Flux，必须在 doOnComplete 中捕获
                                state.setInvestmentPlan(stripJsonBlock(judge.getLastRawResult()));
                                log.info("投资计划已生成");
                            });
                }))
                .doOnComplete(() -> {
                    sink.tryEmitNext(WorkflowEvent.phaseComplete("BullBearDebate"));
                    log.info("多空辩论完成");
                });
    }

    /** 剥离文本中的 ```json``` 代码块，只保留自然语言部分 */
    private static String stripJsonBlock(String text) {
        if (text == null) return null;
        return text.replaceAll("```json\\s*[\\s\\S]*?```\\s*", "").trim();
    }
}
