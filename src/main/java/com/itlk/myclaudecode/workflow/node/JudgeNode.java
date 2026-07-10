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

import java.util.List;

@Slf4j
public class JudgeNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;
    private final UsageRecordService usageRecordService;

    /** 最近一次裁决的原始结果（含 JSON），供 Orchestrator 解析决策用 */
    private volatile String lastRawResult;

    public JudgeNode(ChatModel chatModel, String roleName, String systemPrompt, UsageRecordService usageRecordService) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
        this.usageRecordService = usageRecordService;
    }

    public String getLastRawResult() {
        return lastRawResult;
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

        // 将标的信息和系统时间注入到 system prompt 开头
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        String enrichedSystemPrompt = "【当前时间】" + now + "\n\n"
                + AnalystNode.buildStockTargetLine(state) + "\n\n"
                + "⚠️ 关键约束：\n"
                + "1. 你只能对上述标的做出裁决，禁止引入其他股票的数据\n"
                + "2. 裁决必须基于上游分析师和辩论的数据，禁止使用训练知识\n\n"
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
                    String rawResult = result.toString();
                    lastRawResult = rawResult.replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
                            .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "").trim();
                    // 剥离 JSON 并提取关键决策信息追加到末尾
                    String cleanResult = sanitizeAndExtractDecision(rawResult);
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, cleanResult));
                    log.info("[{}] 裁决完成", roleName);
                    // Record usage
                    try {
                        long inputTokens = UsageRecordService.estimateInputTokensFromText(prompt + enrichedSystemPrompt);
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
                .trim();
    }

    /**
     * 清理 GUARD 标签，剥离 JSON，提取关键决策信息追加到末尾
     */
    private static String sanitizeAndExtractDecision(String text) {
        if (text == null) return "";
        // 先清理 GUARD 标签
        String cleaned = text
                .replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
                .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "")
                .trim();

        // 剥离 ```json 代码块
        cleaned = cleaned.replaceAll("```json\\s*[\\s\\S]*?```\\s*", "").trim();

        // 用状态机扫描并剥离末尾的裸 JSON 对象（支持嵌套花括号）
        String jsonStr = extractAndRemoveTrailingJson(cleaned);
        cleaned = cleaned.trim();

        // 如果提取到了 JSON，解析关键字段追加到末尾
        if (jsonStr != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);
                StringBuilder sb = new StringBuilder();
                String action = node.has("action") ? node.get("action").asText("") : "";
                double confidence = node.has("confidence") ? node.get("confidence").asDouble(0) : 0;
                double targetPrice = node.has("target_price") ? node.get("target_price").asDouble(0) : 0;
                String summary = node.has("summary") ? node.get("summary").asText("") : "";

                if (!action.isEmpty()) {
                    sb.append("**裁决：").append(action).append("**");
                    if (confidence > 0) sb.append(" | 置信度：").append(Math.round(confidence * 100)).append("%");
                    if (targetPrice > 0) sb.append(" | 目标价：¥").append(targetPrice);
                }
                if (!summary.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(summary);
                }
                if (sb.length() > 0) {
                    cleaned = cleaned + "\n\n" + sb;
                }
            } catch (Exception e) {
                log.debug("[JudgeNode] 解析决策 JSON 失败: {}", e.getMessage());
            }
        }
        return cleaned;
    }

    /**
     * 用状态机从文本末尾扫描并提取裸 JSON 对象（正确处理嵌套花括号）
     */
    private static String extractAndRemoveTrailingJson(String text) {
        if (text == null || text.isEmpty()) return null;
        // 从末尾往前找最后一个 }
        int end = text.lastIndexOf('}');
        if (end < 0) return null;
        // 从 end 往前扫描，用括号计数找到匹配的 {
        int depth = 0;
        int start = -1;
        for (int i = end; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                depth--;
                if (depth == 0) {
                    start = i;
                    break;
                }
            }
        }
        if (start < 0) return null;
        // 确认 start 前面是换行或空白（避免误匹配文本中间的花括号）
        if (start > 0) {
            char before = text.charAt(start - 1);
            if (before != '\n' && before != '\r' && !Character.isWhitespace(before)) return null;
        }
        String jsonCandidate = text.substring(start, end + 1).trim();
        // 验证是有效 JSON 且包含决策相关字段
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonCandidate);
            // 必须包含 action 字段才算决策 JSON
            if (node.has("action") || node.has("position_stance") || node.has("arguments")) {
                return jsonCandidate;
            }
        } catch (Exception e) {
            // 不是有效 JSON，忽略
        }
        return null;
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
