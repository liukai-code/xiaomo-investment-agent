package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.conversation.service.UsageRecordService;
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
    private final UsageRecordService usageRecordService;

    public DebateNode(ChatModel chatModel, String roleName, String systemPrompt, UsageRecordService usageRecordService) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
        this.usageRecordService = usageRecordService;
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
        // 将标的信息和系统时间注入到 system prompt 开头
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        String enrichedSystemPrompt = "【当前时间】" + now + "\n\n"
                + AnalystNode.buildStockTargetLine(state) + "\n\n"
                + "⚠️ 关键约束：\n"
                + "1. 你只能围绕上述标的进行辩论，禁止引入其他股票的数据\n"
                + "2. 所有数据必须来自上游分析师的报告，禁止使用训练知识\n\n"
                + systemPrompt;

        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(enrichedSystemPrompt)
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
                .filter(chunk -> !isGuardTag(chunk))
                .map(chunk -> {
                    argument.append(chunk);
                    return WorkflowEvent.debateChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    String fullArgument = sanitizeOutput(argument.toString());
                    DebateMessage msg = new DebateMessage(roleName, fullArgument, Instant.now());
                    debateHistory.add(msg);
                    sink.tryEmitNext(WorkflowEvent.debateComplete(roleName, fullArgument));
                    log.info("[{}] 第 {} 轮辩论完成", roleName, round);
                    // Record usage
                    try {
                        long inputTokens = UsageRecordService.estimateInputTokensFromText(prompt + systemPrompt);
                        usageRecordService.record(state.getUserId(), state.getConversationId(), inputTokens, null, 0);
                    } catch (Exception e) {
                        log.warn("[{}] 记录用量失败: {}", roleName, e.getMessage());
                    }
                });
    }

    private static String sanitizeOutput(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
                .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "")
                // 剥离 ```json 代码块
                .replaceAll("```json\\s*[\\s\\S]*?```\\s*", "")
                // 剥离末尾裸 JSON 对象（如 {"arguments":[...]}）
                .replaceAll("\\n\\s*\\{[\\s\\S]*?}\\s*$", "")
                .trim();
    }

    private static boolean isGuardTag(String chunk) {
        if (chunk == null) return false;
        String trimmed = chunk.trim();
        return trimmed.startsWith("[GUARD:") || trimmed.startsWith("[GUARD_SIGNAL]")
                || trimmed.equals("[/GUARD]") || trimmed.equals("[/GUARD_SIGNAL]");
    }

    private String buildDebatePrompt(WorkflowState state, List<DebateMessage> history, int round) {
        StringBuilder sb = new StringBuilder();

        // 预注入缓存数据
        if (!state.getCachedData().isEmpty()) {
            sb.append("## 已有数据（无需重新获取）\n\n");
            state.getCachedData().forEach((key, value) ->
                    sb.append("### ").append(key).append("\n").append(value).append("\n\n"));
            sb.append("⚠️ 以上数据已由上游分析师获取，无需重复调用工具获取相同数据。\n\n");
        }

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
