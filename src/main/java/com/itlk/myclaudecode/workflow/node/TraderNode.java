package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.agent.service.impl.MaxToolCallManager;
import com.itlk.myclaudecode.tool.guard.FetchSessionTracker;
import com.itlk.myclaudecode.tool.guard.InfoGainTracker;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector;
import com.itlk.myclaudecode.tool.guard.SearchSessionTracker;
import com.itlk.myclaudecode.conversation.service.UsageRecordService;
import com.itlk.myclaudecode.workflow.agent.AgentRole;
import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TraderNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final ToolGuardProperties guardProperties;
    private final AgentRole.RoleGuardConfig roleGuardConfig;
    private final UsageRecordService usageRecordService;

    public TraderNode(ChatModel chatModel, String roleName,
                      String systemPrompt, List<ToolCallback> toolCallbacks,
                      ToolGuardProperties guardProperties,
                      AgentRole.RoleGuardConfig roleGuardConfig,
                      UsageRecordService usageRecordService) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
        this.guardProperties = guardProperties;
        this.usageRecordService = usageRecordService;
        this.roleGuardConfig = roleGuardConfig;
    }

    @Override
    public String name() {
        return roleName;
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("[{}] 开始制定交易方案", roleName);

        // 将标的信息和系统时间注入到 system prompt 开头
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        String enrichedSystemPrompt = "【当前时间】" + now + "\n\n"
                + AnalystNode.buildStockTargetLine(state) + "\n\n"
                + "⚠️ 关键约束：\n"
                + "1. 你只能为上述标的制定交易方案，禁止引入其他股票的数据\n"
                + "2. 交易方案必须基于上游分析师和辩论的数据，禁止使用训练知识\n\n"
                + systemPrompt;

        ChatClient client = ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .defaultSystem(enrichedSystemPrompt)
                .build();

        String prompt = buildTraderPrompt(state);
        sink.tryEmitNext(WorkflowEvent.agentStart(roleName));

        StringBuilder proposal = new StringBuilder();

        // Use role-specific config when available, fall back to global
        AgentRole.RoleGuardConfig rc = roleGuardConfig;
        double infoGainThreshold = rc != null ? rc.infoGainThreshold() : guardProperties.infoGainThreshold();
        int repetitionThreshold = rc != null ? rc.repetitionThreshold() : guardProperties.repetitionThreshold();
        int maxFetches = rc != null ? rc.maxFetches() : guardProperties.maxFetches();
        int maxConsecutiveNoNewInfo = rc != null ? rc.maxConsecutiveNoNewInfo() : guardProperties.maxConsecutiveNoNewInfo();
        int maxSearchRounds = rc != null ? rc.maxSearchRounds() : guardProperties.maxSearchRounds();
        int maxSteps = rc != null && rc.maxSteps() > 0 ? rc.maxSteps() : guardProperties.maxIterations();

        Map<String, Object> toolCtx = new HashMap<>();
        toolCtx.put(MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0));
        toolCtx.put(MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(3, infoGainThreshold));
        toolCtx.put(MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(repetitionThreshold));
        toolCtx.put(MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, new FetchSessionTracker(maxFetches, maxConsecutiveNoNewInfo));
        toolCtx.put(MaxToolCallManager.SEARCH_SESSION_TRACKER_KEY, new SearchSessionTracker(maxSearchRounds));
        toolCtx.put(MaxToolCallManager.MAX_FETCHES_KEY, maxFetches);
        toolCtx.put(MaxToolCallManager.DUPLICATE_CACHE_KEY, new LinkedHashMap<String, List<Message>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                return size() > 50;
            }
        });
        toolCtx.put(MaxToolCallManager.NON_RETRIABLE_CACHE_KEY, new ConcurrentHashMap<String, String>());
        toolCtx.put(MaxToolCallManager.PER_TOOL_CALL_COUNT_KEY, new ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>());
        toolCtx.put(MaxToolCallManager.MAX_STEPS_KEY, maxSteps);
        if (state.getAllowedStockCodes() != null && !state.getAllowedStockCodes().isEmpty()) {
            toolCtx.put(MaxToolCallManager.ALLOWED_STOCK_CODES_KEY, state.getAllowedStockCodes());
        }

        return client.prompt()
                .user(prompt)
                .options(AnthropicChatOptions.builder()
                        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                        .temperature(0.4)
                        .maxTokens(4096)
                        .toolContext(toolCtx)
                        .build())
                .stream()
                .content()
                .filter(chunk -> !isGuardTag(chunk))
                .map(chunk -> {
                    proposal.append(chunk);
                    return WorkflowEvent.agentChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    String fullProposal = sanitizeOutput(proposal.toString());
                    state.setTradingProposal(fullProposal);
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, fullProposal));
                    log.info("[{}] 交易方案制定完成", roleName);
                    // Record usage
                    try {
                        AtomicInteger toolCounter = (AtomicInteger) toolCtx.get(MaxToolCallManager.TOOL_CALL_COUNTER_KEY);
                        int toolCalls = toolCounter != null ? toolCounter.get() : 0;
                        long inputTokens = UsageRecordService.estimateInputTokensFromText(prompt + enrichedSystemPrompt);
                        usageRecordService.record(state.getUserId(), state.getConversationId(), inputTokens, null, toolCalls);
                        state.getTotalEstimatedTokens().addAndGet(inputTokens);
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
                // 剥离末尾裸 JSON 对象（如 {"position_stance":"..."}）
                .replaceAll("\\n\\s*\\{[\\s\\S]*?}\\s*$", "")
                .trim();
    }

    private static boolean isGuardTag(String chunk) {
        if (chunk == null) return false;
        String trimmed = chunk.trim();
        return trimmed.startsWith("[GUARD:") || trimmed.startsWith("[GUARD_SIGNAL]")
                || trimmed.equals("[/GUARD]") || trimmed.equals("[/GUARD_SIGNAL]");
    }

    private String buildTraderPrompt(WorkflowState state) {
        StringBuilder sb = new StringBuilder();

        // 预注入缓存数据
        if (!state.getCachedData().isEmpty()) {
            sb.append("## 已有数据（无需重新获取）\n\n");
            state.getCachedData().forEach((key, value) ->
                    sb.append("### ").append(key).append("\n").append(value).append("\n\n"));
            sb.append("⚠️ 以上数据已由上游分析师获取，无需重复调用工具获取相同数据。\n\n");
        }

        sb.append("## 投资计划\n\n").append(state.getInvestmentPlan()).append("\n\n");

        sb.append("## 分析报告\n\n");
        state.getAnalystReports().forEach((name, report) ->
                sb.append("### ").append(name).append("\n").append(report.reportContent()).append("\n\n"));

        sb.append("\n请基于以上投资计划和分析报告，制定具体的交易方案。\n");
        sb.append("包含：建仓/减仓策略、价格区间、仓位比例、止损止盈设置。");
        return sb.toString();
    }
}
