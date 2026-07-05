package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.agent.service.impl.MaxToolCallManager;
import com.itlk.myclaudecode.tool.guard.FetchSessionTracker;
import com.itlk.myclaudecode.tool.guard.InfoGainTracker;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector;
import com.itlk.myclaudecode.tool.guard.ReportCompletenessChecker;
import com.itlk.myclaudecode.tool.guard.SearchSessionTracker;
import com.itlk.myclaudecode.workflow.agent.AgentRole;
import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.AgentReport;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AnalystNode implements WorkflowNode {

    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*(\\{[\\s\\S]*?})\\s*```|\\{[\\s\\S]*?}");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final ToolGuardProperties guardProperties;
    private final AgentRole.RoleGuardConfig roleGuardConfig;

    public AnalystNode(ChatModel chatModel, String roleName,
                       String systemPrompt, List<ToolCallback> toolCallbacks,
                       ToolGuardProperties guardProperties,
                       AgentRole.RoleGuardConfig roleGuardConfig) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
        this.guardProperties = guardProperties;
        this.roleGuardConfig = roleGuardConfig;
    }

    @Override
    public String name() {
        return roleName;
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("[{}] 开始执行数据采集", roleName);

        // 将标的信息和系统时间注入到 system prompt 开头
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        String enrichedSystemPrompt = "【当前时间】" + now + "\n\n"
                + buildStockTargetLine(state) + "\n\n"
                + "⚠️ 关键约束：\n"
                + "1. 你只能分析上述标的，禁止分析其他股票\n"
                + "2. 所有工具调用和数据获取必须针对该标的，stockCode 参数必须使用上述代码\n"
                + "3. 报告中的数据必须来自工具返回，禁止使用训练知识\n"
                + "4. 如果工具调用失败，如实报告，禁止用旧数据填充\n"
                + "5. 禁止重新查询或猜测股票代码，标的已锁定\n\n"
                + systemPrompt;

        ChatClient agentClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .defaultSystem(enrichedSystemPrompt)
                .build();

        String stockDesc = state.getResolvedStockName() != null
                ? state.getResolvedStockName() + "（" + state.getResolvedStockCode() + "）"
                : state.getResolvedStockCode();
        String userPrompt = "请使用可用工具获取" + stockDesc + "的数据，产出一份结构化的分析报告。"
                + "所有 stockCode 参数必须使用 " + state.getResolvedStockCode() + "。";

        sink.tryEmitNext(WorkflowEvent.agentStart(roleName));

        StringBuilder report = new StringBuilder();
        ReportCompletenessChecker completenessChecker = new ReportCompletenessChecker(
                guardProperties.reportMinLength(), guardProperties.reportMinSections());

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
        toolCtx.put(MaxToolCallManager.MAX_STEPS_KEY, maxSteps);
        if (state.getAllowedStockCodes() != null && !state.getAllowedStockCodes().isEmpty()) {
            toolCtx.put(MaxToolCallManager.ALLOWED_STOCK_CODES_KEY, state.getAllowedStockCodes());
        }
        toolCtx.put(MaxToolCallManager.REPORT_COMPLETENESS_KEY, completenessChecker);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .temperature(0.4)
                .maxTokens(8192)
                .toolContext(toolCtx)
                .build();

        return agentClient.prompt()
                .user(userPrompt)
                .options(options)
                .stream()
                .content()
                .filter(chunk -> !isGuardTag(chunk))
                .map(chunk -> {
                    report.append(chunk);
                    completenessChecker.appendChunk(chunk);
                    return WorkflowEvent.agentChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    String fullReport = sanitizeOutput(report.toString());
                    state.getAnalystReports().put(roleName,
                            new AgentReport(roleName, fullReport, Instant.now()));
                    // 缓存报告内容供下游 Agent 复用
                    state.getCachedData().put(roleName + "_opinion", fullReport);
                    // 尝试提取结构化 JSON 数据
                    extractStructuredData(fullReport).ifPresent(json ->
                            state.getCachedData().put(roleName + "_structured", json));
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, fullReport));
                    log.info("[{}] 数据采集完成，报告长度: {}", roleName, fullReport.length());
                })
                .doOnError(e -> log.error("[{}] 数据采集错误: {}", roleName, e.getMessage()));
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

    /**
     * 构建标的锁定行，优先使用 resolvedStockCode/Name，fallback 到 originalQuery
     */
    static String buildStockTargetLine(WorkflowState state) {
        if (state.getResolvedStockCode() != null) {
            String code = state.getResolvedStockCode();
            String name = state.getResolvedStockName() != null ? state.getResolvedStockName() : "";
            return "【分析标的】" + name + "（" + code + "）\n"
                    + "⚠️ 标的已锁定为 " + code + "，你必须严格遵守以下约束：\n"
                    + "1. 所有工具调用必须使用 stockCode=\"" + code + "\"，禁止使用其他代码\n"
                    + "2. 禁止重新查询、猜测或推断股票代码\n"
                    + "3. 禁止引用、分析、对比任何非 " + code + " 的公司\n"
                    + "4. 即使在行业分析、板块分析、概念分析中，也不得提及其他股票名称或代码\n"
                    + "5. 若工具返回包含其他股票的数据，该数据已被系统过滤，你只能使用剩余数据\n"
                    + "6. 你的分析范围被严格限制在 " + name + "（" + code + "）这一只股票内\n"
                    + "7. 工具调用失败时，必须根据错误提示修正参数后重试，禁止 fallback 到训练知识或历史数据\n"
                    + "8. 禁止使用训练数据中的任何股票信息，所有数据必须来自工具实时返回";
        }
        // fallback：只有 originalQuery 的情况（理论上不应发生）
        return "【分析标的】" + state.getOriginalQuery();
    }

    private static java.util.Optional<String> extractStructuredData(String report) {
        try {
            Matcher matcher = JSON_PATTERN.matcher(report);
            if (matcher.find()) {
                String json = matcher.group(1) != null ? matcher.group(1) : matcher.group(0);
                // 验证是有效 JSON
                objectMapper.readTree(json);
                return java.util.Optional.of(json);
            }
        } catch (Exception e) {
            log.debug("[{}] 报告不包含有效JSON，使用纯文本", "AnalystNode");
        }
        return java.util.Optional.empty();
    }
}
