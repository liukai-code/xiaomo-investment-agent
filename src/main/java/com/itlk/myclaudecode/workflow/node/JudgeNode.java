package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.DebateMessage;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
public class JudgeNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;

    public JudgeNode(ChatModel chatModel, String roleName, String systemPrompt) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String name() {
        return roleName;
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        return makeJudgment(state, state.getBullBearDebate(), sink);
    }

    /**
     * 做出裁决
     */
    public Flux<WorkflowEvent> makeJudgment(WorkflowState state,
                                             List<DebateMessage> debateHistory,
                                             Sinks.Many<WorkflowEvent> sink) {
        log.info("[{}] 开始裁决", roleName);

        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();

        String prompt = buildJudgmentPrompt(state, debateHistory);
        sink.tryEmitNext(WorkflowEvent.agentStart(roleName));

        StringBuilder result = new StringBuilder();

        return client.prompt()
                .user(prompt)
                .options(AnthropicChatOptions.builder()
                        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                        .temperature(0.3)
                        .maxTokens(4096)
                        .build())
                .stream()
                .content()
                .map(chunk -> {
                    result.append(chunk);
                    return WorkflowEvent.agentChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, result.toString()));
                    log.info("[{}] 裁决完成", roleName);
                });
    }

    private String buildJudgmentPrompt(WorkflowState state, List<DebateMessage> debateHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 分析报告\n\n");
        state.getAnalystReports().forEach((name, report) ->
                sb.append("### ").append(name).append("\n").append(report.reportContent()).append("\n\n"));

        if (debateHistory != null && !debateHistory.isEmpty()) {
            sb.append("## 辩论记录\n\n");
            for (DebateMessage msg : debateHistory) {
                sb.append("**").append(msg.speakerName()).append("**: ")
                        .append(msg.argument()).append("\n\n");
            }
        }

        if (state.getTradingProposal() != null) {
            sb.append("## 交易方案\n\n").append(state.getTradingProposal()).append("\n\n");
        }

        sb.append("\n请综合以上所有信息，给出你的裁决。");
        return sb.toString();
    }
}
