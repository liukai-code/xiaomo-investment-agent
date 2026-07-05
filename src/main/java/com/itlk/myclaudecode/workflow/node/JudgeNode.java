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

        // 将标的信息注入到 system prompt 开头
        String enrichedSystemPrompt = "【分析标的】" + state.getOriginalQuery() + "\n\n"
                + "⚠️ 你只能对上述标的做出裁决，禁止引入其他股票的数据。\n\n"
                + systemPrompt;

        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(enrichedSystemPrompt)
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
                .filter(chunk -> !isGuardTag(chunk))
                .map(chunk -> {
                    result.append(chunk);
                    return WorkflowEvent.agentChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    String fullResult = sanitizeOutput(result.toString());
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, fullResult));
                    log.info("[{}] 裁决完成", roleName);
                });
    }

    private static String sanitizeOutput(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
                .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "")
                .trim();
    }

    private static boolean isGuardTag(String chunk) {
        if (chunk == null) return false;
        String trimmed = chunk.trim();
        return trimmed.startsWith("[GUARD:") || trimmed.startsWith("[GUARD_SIGNAL]")
                || trimmed.equals("[/GUARD]") || trimmed.equals("[/GUARD_SIGNAL]");
    }

    private String buildJudgmentPrompt(WorkflowState state, List<DebateMessage> debateHistory) {
        StringBuilder sb = new StringBuilder();

        // 预注入缓存数据
        if (!state.getCachedData().isEmpty()) {
            sb.append("## 已有数据（无需重新获取）\n\n");
            state.getCachedData().forEach((key, value) ->
                    sb.append("### ").append(key).append("\n").append(value).append("\n\n"));
            sb.append("⚠️ 以上数据已由上游分析师获取，无需重复调用工具获取相同数据。\n\n");
        }

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
