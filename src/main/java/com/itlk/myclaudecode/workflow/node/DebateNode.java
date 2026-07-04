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

import java.time.Instant;
import java.util.List;

@Slf4j
public class DebateNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;

    public DebateNode(ChatModel chatModel, String roleName, String systemPrompt) {
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
        throw new UnsupportedOperationException("辩论节点请通过 debateRound() 调用");
    }

    /**
     * 执行一轮辩论
     */
    public Flux<WorkflowEvent> debateRound(WorkflowState state,
                                            List<DebateMessage> debateHistory,
                                            Sinks.Many<WorkflowEvent> sink) {
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();

        int round = countRounds(debateHistory) + 1;
        String prompt = buildDebatePrompt(state, debateHistory, round);

        log.info("[{}] 开始第 {} 轮辩论", roleName, round);
        sink.tryEmitNext(WorkflowEvent.debateStart(roleName, round));

        StringBuilder argument = new StringBuilder();

        return client.prompt()
                .user(prompt)
                .options(AnthropicChatOptions.builder()
                        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                        .temperature(0.5)
                        .maxTokens(4096)
                        .build())
                .stream()
                .content()
                .map(chunk -> {
                    argument.append(chunk);
                    String sanitized = sanitizeOutput(argument.toString());
                    return WorkflowEvent.debateChunk(roleName, sanitized);
                })
                .doOnComplete(() -> {
                    String fullArgument = sanitizeOutput(argument.toString());
                    DebateMessage msg = new DebateMessage(roleName, fullArgument, Instant.now());
                    debateHistory.add(msg);
                    sink.tryEmitNext(WorkflowEvent.debateComplete(roleName, fullArgument));
                    log.info("[{}] 第 {} 轮辩论完成", roleName, round);
                });
    }

    private static String sanitizeOutput(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
                .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "")
                .trim();
    }

    private String buildDebatePrompt(WorkflowState state, List<DebateMessage> history, int round) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 分析报告汇总\n\n");
        state.getAnalystReports().forEach((name, report) ->
                sb.append("### ").append(name).append("\n").append(report.reportContent()).append("\n\n"));

        if (!history.isEmpty()) {
            sb.append("## 之前的辩论记录\n\n");
            for (DebateMessage msg : history) {
                sb.append("**").append(msg.speakerName()).append("**:\n")
                        .append(msg.argument()).append("\n\n");
            }
        }

        sb.append("## 当前轮次：第").append(round).append("轮\n");
        sb.append("请基于以上信息，从你的角度给出论证。如果是后续轮次，请针对对方观点进行反驳并提出新论据。");
        return sb.toString();
    }

    private int countRounds(List<DebateMessage> history) {
        return (int) history.stream()
                .filter(m -> m.speakerName().equals(roleName))
                .count();
    }
}
