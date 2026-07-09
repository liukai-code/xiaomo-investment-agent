package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.agent.service.ChatStreamEvent;
import com.itlk.myclaudecode.common.util.DebugFileLogger;
import com.itlk.myclaudecode.tool.guard.FetchSessionTracker;
import com.itlk.myclaudecode.tool.guard.GuardSignal;
import com.itlk.myclaudecode.tool.guard.ReportCompletenessChecker;
import com.itlk.myclaudecode.tool.guard.SearchSessionTracker;
import com.itlk.myclaudecode.tool.guard.InfoGainTracker;
import com.itlk.myclaudecode.tool.guard.InfoGainTracker.InfoGainLevel;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector.RepetitionResult;
import com.itlk.myclaudecode.tool.guard.ToolBehaviorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MaxToolCallManager implements ToolCallingManager {

    public static final String TOOL_CALL_COUNTER_KEY = "toolCallCounter";
    public static final String INFO_GAIN_TRACKER_KEY = "infoGainTracker";
    public static final String REPETITION_DETECTOR_KEY = "repetitionDetector";
    public static final String FETCH_SESSION_TRACKER_KEY = "fetchSessionTracker";
    public static final String SEARCH_SESSION_TRACKER_KEY = "searchSessionTracker";
    public static final String DUPLICATE_CACHE_KEY = "duplicateCache";
    public static final String REPORT_COMPLETENESS_KEY = "reportCompleteness";
    public static final String MAX_FETCHES_KEY = "maxFetches";
    public static final String NON_RETRIABLE_CACHE_KEY = "nonRetriableCache";
    public static final String MAX_STEPS_KEY = "maxSteps";
    public static final String ALLOWED_STOCK_CODES_KEY = "allowedStockCodes";
    public static final String RESOLVED_STOCK_NAME_KEY = "resolvedStockName";
    public static final String STATUS_SINK_KEY = "statusSink";
    public static final String PER_TOOL_CALL_COUNT_KEY = "perToolCallCount";

    private static final Set<String> FETCH_TOOL_NAMES = Set.of(
            "fetchArticleContent", "fetchWebpage"
    );

    private static final Set<String> SEARCH_TOOL_NAMES = Set.of(
            "bailian_web_search", "webSearch", "web_search"
    );

    /** 标的已锁定时，禁止调用的无关工具 */
    private static final Set<String> STOCK_IRRELEVANT_TOOLS = Set.of(
            "getMyHoldings", "getMyAccountSummary"
    );

    private final ToolCallingManager delegate;
    private final ToolGuardProperties properties;
    private final ToolBehaviorRegistry behaviorRegistry;
    private final long toolTimeoutMs;

    public MaxToolCallManager(
            ToolCallbackResolver toolCallbackResolver,
            ToolExecutionExceptionProcessor exceptionProcessor,
            ToolGuardProperties properties,
            ToolBehaviorRegistry behaviorRegistry) {
        this.properties = properties;
        this.behaviorRegistry = behaviorRegistry;
        this.toolTimeoutMs = properties.toolTimeoutSeconds() * 1000L;
        this.delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver)
                .toolExecutionExceptionProcessor(exceptionProcessor)
                .build();
        log.info("MaxToolCallManager 初始化, softLimit={}, escalationWarning={}, escalationFinal={}, hardLimit={}",
                properties.softLimit(), properties.escalationWarning(), properties.escalationFinal(), properties.maxIterations());
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        AtomicInteger counter = extractFromContext(prompt, TOOL_CALL_COUNTER_KEY, AtomicInteger.class);
        InfoGainTracker infoGainTracker = extractFromContext(prompt, INFO_GAIN_TRACKER_KEY, InfoGainTracker.class);
        RepetitionDetector repetitionDetector = extractFromContext(prompt, REPETITION_DETECTOR_KEY, RepetitionDetector.class);
        FetchSessionTracker fetchTracker = extractFromContext(prompt, FETCH_SESSION_TRACKER_KEY, FetchSessionTracker.class);
        SearchSessionTracker searchTracker = extractFromContext(prompt, SEARCH_SESSION_TRACKER_KEY, SearchSessionTracker.class);
        Map<String, List<Message>> duplicateCache = extractFromContext(prompt, DUPLICATE_CACHE_KEY, Map.class);
        ReportCompletenessChecker completenessChecker = extractFromContext(prompt, REPORT_COMPLETENESS_KEY, ReportCompletenessChecker.class);
        Integer maxFetchesCtx = extractFromContext(prompt, MAX_FETCHES_KEY, Integer.class);
        int maxFetches = maxFetchesCtx != null ? maxFetchesCtx : properties.maxFetches();
        Map<String, String> nonRetriableCache = extractFromContext(prompt, NON_RETRIABLE_CACHE_KEY, Map.class);
        Integer maxStepsCtx = extractFromContext(prompt, MAX_STEPS_KEY, Integer.class);
        java.util.Set<String> allowedStockCodes = extractFromContext(prompt, ALLOWED_STOCK_CODES_KEY, java.util.Set.class);
        String resolvedStockName = extractFromContext(prompt, RESOLVED_STOCK_NAME_KEY, String.class);
        int effectiveMaxIterations = maxStepsCtx != null ? Math.min(maxStepsCtx, properties.maxIterations()) : properties.maxIterations();
        Map<String, AtomicInteger> perToolCallCount = extractFromContext(prompt, PER_TOOL_CALL_COUNT_KEY, Map.class);

        int step = counter.incrementAndGet();
        AssistantMessage assistantWithTools = findAssistantWithToolCalls(chatResponse);
        List<AssistantMessage.ToolCall> toolCalls = assistantWithTools.getToolCalls();
        String toolNames = toolCalls.stream().map(AssistantMessage.ToolCall::name).collect(java.util.stream.Collectors.joining(", "));
        log.info("[MaxToolCallManager] 工具调用轮次: {}/{}, 工具: [{}]", step, effectiveMaxIterations, toolNames);

        // 发射工具调用状态事件到前端
        @SuppressWarnings("unchecked")
        Sinks.Many<ChatStreamEvent> statusSink = extractFromContext(prompt, STATUS_SINK_KEY, Sinks.Many.class);
        log.info("[MaxToolCallManager] statusSink={}, sinkPresent={}", statusSink != null ? "存在" : "null", statusSink != null);
        if (statusSink != null) {
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ChatStreamEvent event = ChatStreamEvent.toolCall(tc.name(), step, effectiveMaxIterations);
                log.info("[MaxToolCallManager] 发射 TOOL_CALL 事件: toolName={}, step={}", tc.name(), step);
                statusSink.tryEmitNext(event);
            }
        }
        ToolExecutionResult result;

        // 硬限制检查：迭代硬上限阻止所有工具调用；fetch 硬上限只阻止 fetch 类工具
        boolean overMaxIterations = step > effectiveMaxIterations;
        boolean overMaxFetch = fetchTracker != null && fetchTracker.isOverMaxFetches();
        if (overMaxIterations) {
            String blockMsg = "[GUARD: FORCE]\nAction: 已达到最大工具调用轮次限制，必须立即停止所有工具调用。\nContext: step=" + step + "/" + effectiveMaxIterations + "\n[/GUARD]\n\n你已经没有可用的工具调用次数了。请直接基于已获取的数据完成分析报告，输出完整内容。不要再请求任何工具。";
            log.warn("[MaxToolCallManager] 已超过迭代硬上限 ({}/{}), 阻止工具调用", step, effectiveMaxIterations);
            List<Message> blockHistory = new ArrayList<>();
            blockHistory.add(assistantWithTools);
            List<ToolResponseMessage.ToolResponse> blockResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                blockResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), blockMsg));
            }
            blockHistory.add(new ToolResponseMessage(blockResponses));
            return new CachedToolExecutionResult(blockHistory);
        }
        if (overMaxFetch) {
            boolean allFetchTools = toolCalls.stream().allMatch(tc -> isFetchTool(tc.name()));
            if (allFetchTools) {
                String blockMsg = "[GUARD: FORCE]\nAction: 已达到最大抓取次数限制，禁止继续抓取。\nContext: step=" + step + "/" + effectiveMaxIterations + ", fetch=over\n[/GUARD]\n\n抓取次数已达上限，请使用已获取的数据完成分析，不要再调用抓取工具。";
                log.warn("[MaxToolCallManager] 已超过 fetch 硬上限, 阻止 fetch 工具调用");
                List<Message> blockHistory = new ArrayList<>();
                blockHistory.add(assistantWithTools);
                List<ToolResponseMessage.ToolResponse> blockResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : toolCalls) {
                    blockResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), blockMsg));
                }
                blockHistory.add(new ToolResponseMessage(blockResponses));
                return new CachedToolExecutionResult(blockHistory);
            }
            // 非 fetch 工具不受 fetch 上限限制，继续执行
            log.info("[MaxToolCallManager] fetch 硬上限已达，但当前工具非 fetch 类，允许继续执行");
        }

        // 同工具调用次数限制：防止同一工具被无限循环调用
        if (perToolCallCount != null) {
            for (AssistantMessage.ToolCall tc : toolCalls) {
                AtomicInteger toolCount = perToolCallCount.computeIfAbsent(tc.name(), k -> new AtomicInteger(0));
                int count = toolCount.incrementAndGet();
                if (count > properties.maxSameToolCalls()) {
                    log.warn("[MaxToolCallManager] 同工具调用超限: {} 已调用 {} 次 (上限 {}), 强制停止",
                            tc.name(), count, properties.maxSameToolCalls());
                    String blockMsg = "[GUARD: FORCE]\nAction: 工具 " + tc.name() + " 已被调用 " + count + " 次，超过单工具调用上限(" + properties.maxSameToolCalls() + "次)。\n[/GUARD]\n\n该工具已被反复调用过多轮，请直接基于已有的工具返回数据完成分析报告。不要再调用 " + tc.name() + " 工具。";
                    List<Message> blockHistory = new ArrayList<>();
                    blockHistory.add(assistantWithTools);
                    List<ToolResponseMessage.ToolResponse> blockResponses = new ArrayList<>();
                    for (AssistantMessage.ToolCall t : toolCalls) {
                        blockResponses.add(new ToolResponseMessage.ToolResponse(t.id(), t.name(), blockMsg));
                    }
                    blockHistory.add(new ToolResponseMessage(blockResponses));
                    return new CachedToolExecutionResult(blockHistory);
                }
            }
        }

        if (toolCalls.size() == 1) {
            AssistantMessage.ToolCall tc = toolCalls.get(0);
            boolean cacheable = behaviorRegistry.getBehavior(tc.name()).cacheable();
            boolean isFetchTool = isFetchTool(tc.name());

            // Report completeness check (workflow mode)
            if (completenessChecker != null && completenessChecker.isReportSubstantial()) {
                log.info("[MaxToolCallManager] 报告已完整(length={}), 跳过工具调用",
                        completenessChecker.getAccumulatedLength());
                String msg = "报告已基本完整(length=" + completenessChecker.getAccumulatedLength()
                        + "字符)，请基于已有数据完成报告，无需继续调用工具。";
                List<Message> skipHistory = buildSkipResult(tc, msg);
                GuardSignal signal = new GuardSignal(
                        step, properties.softLimit(), effectiveMaxIterations,
                        properties.escalationWarning(), properties.escalationFinal(),
                        InfoGainLevel.UNKNOWN, 0.0, RepetitionResult.NONE, tc.name(),
                        false, 0, maxFetches, false, 0, false, false);
                return new GuardedToolExecutionResult(new CachedToolExecutionResult(skipHistory), signal);
            }

            // URL dedup check (pre-execution intercept)
            if (isFetchTool && fetchTracker != null) {
                String url = extractUrlFromArgs(tc.arguments());
                if (url != null && fetchTracker.isUrlVisited(url)) {
                    log.info("[MaxToolCallManager] 检测到重复URL，跳过抓取: {}", url);
                    String skipMsg = "该URL已在本次会话中抓取过，请使用之前的搜索结果。已抓取URL数: " + fetchTracker.getVisitedUrlCount();
                    List<Message> skipHistory = buildSkipResult(tc, skipMsg);
                    InfoGainLevel infoGain = infoGainTracker.recordAndGetLevel(skipMsg);
                    RepetitionResult repetition = repetitionDetector.recordAndDetect(tc.name(), tc.arguments());

                    GuardSignal signal = new GuardSignal(
                            step, properties.softLimit(), effectiveMaxIterations,
                            properties.escalationWarning(), properties.escalationFinal(),
                            infoGain, infoGainTracker.getLastSimilarity(),
                            repetition, tc.name(),
                            true, fetchTracker.getFetchCount(), maxFetches, true,
                            0, false, false);
                    return new GuardedToolExecutionResult(new CachedToolExecutionResult(skipHistory), signal);
                }
            }

            // Non-retriable error cache check
            if (nonRetriableCache != null) {
                String nrKey = tc.name() + ":" + tc.arguments().hashCode();
                String cachedError = nonRetriableCache.get(nrKey);
                if (cachedError != null) {
                    log.info("[MaxToolCallManager] 检测到不可重试错误缓存，跳过工具调用: {}", tc.name());
                    List<Message> skipHistory = buildSkipResult(tc, cachedError);
                    return new CachedToolExecutionResult(skipHistory);
                }
            }

            // 工具相关性守卫：标的已锁定时，禁止调用持仓等无关工具
            if (allowedStockCodes != null && !allowedStockCodes.isEmpty()
                    && STOCK_IRRELEVANT_TOOLS.contains(tc.name())) {
                String scopeMsg = "当前正在分析标的，请专注于该标的的数据分析，不要调用" + tc.name()
                        + "。请使用 market_data 或 a_stock_* 工具查询标的行情和基本面数据。";
                log.info("[MaxToolCallManager] 工具相关性守卫拦截: tool={}, 标的={}", tc.name(), allowedStockCodes);
                DebugFileLogger.logGuard("TOOL_RELEVANCE", tc.name(),
                        "BLOCKED | 标的=" + allowedStockCodes + " | 无关工具");
                List<Message> skipHistory = buildSkipResult(tc, scopeMsg);
                return new CachedToolExecutionResult(skipHistory);
            }

            // Stock scope guard check + auto-injection
            if (allowedStockCodes != null && !allowedStockCodes.isEmpty() && isAStockTool(tc.name())) {
                java.util.Set<String> requestedCodes = extractStockCodesFromArgs(tc.arguments());
                DebugFileLogger.logGuard("STOCK_SCOPE", tc.name(),
                        "allowedStockCodes=" + allowedStockCodes + " | requestedCodes=" + requestedCodes + " | args=" + tc.arguments());
                if (!requestedCodes.isEmpty() && !allowedStockCodes.containsAll(requestedCodes)) {
                    // LLM 传了错误的代码 → 拦截
                    java.util.Set<String> disallowed = new java.util.HashSet<>(requestedCodes);
                    disallowed.removeAll(allowedStockCodes);
                    String scopeMsg = "股票代码 " + disallowed + " 不在分析范围内。请只查询目标股票: " + allowedStockCodes;
                    log.info("[MaxToolCallManager] 股票范围守卫拦截: 请求={}, 允许={}", requestedCodes, allowedStockCodes);
                    DebugFileLogger.logGuard("STOCK_SCOPE", tc.name(), "BLOCKED | wrong code: " + disallowed);
                    List<Message> skipHistory = buildSkipResult(tc, scopeMsg);
                    return new CachedToolExecutionResult(skipHistory);
                }
                if (requestedCodes.isEmpty()) {
                    // LLM 没传 stockCode → 自动注入，避免工具报错导致 LLM fallback 到训练知识
                    String targetCode = allowedStockCodes.iterator().next();
                    String injected = injectStockCode(tc.arguments(), tc.name(), targetCode);
                    if (!injected.equals(tc.arguments())) {
                        log.info("[MaxToolCallManager] 自动注入 stockCode={}: {} → {}", targetCode, tc.arguments(), injected);
                        DebugFileLogger.logGuard("STOCK_SCOPE", tc.name(), "AUTO_INJECT | code=" + targetCode);
                        tc = new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), injected);
                    }
                }
            }

            // Date auto-injection: ensure AStock tools always use today's date
            if (isAStockTool(tc.name())) {
                String injected = injectCurrentDate(tc.arguments(), tc.name());
                if (!injected.equals(tc.arguments())) {
                    log.info("[MaxToolCallManager] 自动注入当前日期: {} → {}", tc.arguments(), injected);
                    tc = new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), injected);
                }
            }

            // Normal execution with cache check
            if (cacheable && duplicateCache != null) {
                String cacheKey = tc.name() + ":" + tc.arguments().hashCode();
                List<Message> cachedResult = duplicateCache.get(cacheKey);
                if (cachedResult != null) {
                    log.info("[MaxToolCallManager] 检测到重复工具调用，返回缓存结果: {}", tc.name());
                    return new CachedToolExecutionResult(cachedResult);
                }
                result = executeSafely(prompt, chatResponse);
                duplicateCache.put(cacheKey, result.conversationHistory());
            } else {
                result = executeSafely(prompt, chatResponse);
            }

            // Info gain and repetition detection
            String resultText = extractResultText(result);

            // Inject stock identifier + date header: force LLM to associate data with target stock and current date
            if (allowedStockCodes != null && !allowedStockCodes.isEmpty() && isAStockTool(tc.name())) {
                String targetCode = allowedStockCodes.iterator().next();
                String stockLabel = resolvedStockName != null
                        ? resolvedStockName + "（" + targetCode + "）"
                        : targetCode;
                String today = java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
                String header = "【数据归属：" + stockLabel + " | 当前日期：" + today
                        + " | 禁止用于其他标的或日期的分析】\n";
                String footer = "\n⚠️ 以上数据属于 " + stockLabel + "，请基于此数据分析该标的，禁止分析其他标的。";
                result = wrapToResult(result, tc, header, footer);
                resultText = extractResultText(result);
            }

            // Data pollution hard filter: remove non-target stock data from tool results
            if (allowedStockCodes != null && !allowedStockCodes.isEmpty() && isAStockTool(tc.name())) {
                java.util.Set<String> foreignCodes = extractForeignStockCodes(resultText, allowedStockCodes);
                DebugFileLogger.logGuard("DATA_POLLUTION", tc.name(),
                        "allowedCodes=" + allowedStockCodes + " | foreignCodes=" + foreignCodes + " | resultLen=" + resultText.length());
                if (!foreignCodes.isEmpty()) {
                    log.info("[MaxToolCallManager] 检测到非目标标的数据，执行硬过滤: {}", foreignCodes);
                    result = filterToolResult(result, tc, resultText, allowedStockCodes, foreignCodes);
                    resultText = extractResultText(result);
                }
            }

            InfoGainLevel infoGain = infoGainTracker.recordAndGetLevel(resultText);

            // Cache non-retriable errors
            if (nonRetriableCache != null && isNonRetriableError(resultText)) {
                String nrKey = tc.name() + ":" + tc.arguments().hashCode();
                nonRetriableCache.put(nrKey, resultText);
                log.info("[MaxToolCallManager] 缓存不可重试错误: {}", tc.name());
            }
            RepetitionResult repetition = repetitionDetector.recordAndDetect(tc.name(), tc.arguments());

            // Search tool: limit search rounds
            if (isSearchTool(tc.name()) && searchTracker != null) {
                SearchSessionTracker.SearchResult sr = searchTracker.recordSearch();
                if (sr.isOverLimit()) {
                    log.info("[MaxToolCallManager] 搜索次数超限({}/{}), 拒绝搜索", sr.totalSearches(), properties.maxSearchRounds());
                    String limitMsg = "本次会话搜索次数已达上限。请立即停止所有工具调用，基于已有的搜索结果直接回答用户。不要再调用搜索或抓取工具。";
                    List<Message> limitHistory = buildSkipResult(tc, limitMsg);
                    GuardSignal signal = new GuardSignal(
                            step, properties.softLimit(), effectiveMaxIterations,
                            properties.escalationWarning(), properties.escalationFinal(),
                            infoGain, infoGainTracker.getLastSimilarity(),
                            repetition, tc.name(),
                            false, 0, maxFetches, false, 0, false, false);
                    return new GuardedToolExecutionResult(new CachedToolExecutionResult(limitHistory), signal);
                }
            }

            // Fetch session tracking
            boolean overMaxFetches = false;
            boolean stuckNoNewInfo = false;
            boolean isDuplicateUrl = false;
            int fetchCount = 0;
            int consecutiveNoNewInfo = 0;

            if (isFetchTool && fetchTracker != null) {
                String url = extractUrlFromArgs(tc.arguments());
                FetchSessionTracker.FetchResult fetchResult = fetchTracker.recordFetch(url, infoGain);
                fetchCount = fetchResult.totalFetches();
                isDuplicateUrl = fetchResult.isDuplicateUrl();
                consecutiveNoNewInfo = fetchResult.consecutiveNoNewInfo();
                overMaxFetches = fetchTracker.isOverMaxFetches();
                stuckNoNewInfo = fetchTracker.isStuckNoNewInfo();

                log.info("[MaxToolCallManager] Fetch tracking: count={}, dup={}, consecNoNew={}, overMax={}, stuck={}",
                        fetchCount, isDuplicateUrl, consecutiveNoNewInfo, overMaxFetches, stuckNoNewInfo);
            }

            GuardSignal signal = new GuardSignal(
                    step, properties.softLimit(), effectiveMaxIterations,
                    properties.escalationWarning(), properties.escalationFinal(),
                    infoGain, infoGainTracker.getLastSimilarity(),
                    repetition, tc.name(),
                    isFetchTool, fetchCount, maxFetches, isDuplicateUrl,
                    consecutiveNoNewInfo, overMaxFetches, stuckNoNewInfo);

            log.info("[MaxToolCallManager] 信号: level={}, infoGain={}, repetition={}, shouldInject={}, fetch={}",
                    signal.getLevel(), infoGain, repetition, signal.shouldInject(), isFetchTool);

            if (signal.isHardLimit()) {
                if (overMaxFetches) {
                    log.warn("[MaxToolCallManager] 达到 fetch 硬上限 (fetchCount={}), 强制停止", fetchCount);
                } else {
                    log.warn("[MaxToolCallManager] 达到迭代硬上限 ({}/{}), 强制停止", step, effectiveMaxIterations);
                }
                return new GuardedToolExecutionResult(result, signal);
            }

            if (signal.shouldInject()) {
                // No more counter reset — graduated escalation handles the progression
                return new GuardedToolExecutionResult(result, signal);
            }
        } else {
            result = executeSafely(prompt, chatResponse);
        }

        // 发射工具调用完成事件
        if (statusSink != null) {
            for (AssistantMessage.ToolCall tc : toolCalls) {
                statusSink.tryEmitNext(ChatStreamEvent.toolResult(tc.name()));
            }
        }

        return result;
    }

    public void reset() {
        // No-op: cache is now in toolContext, managed per-session
    }

    @SuppressWarnings("unchecked")
    private <T> T extractFromContext(Prompt prompt, String key, Class<T> type) {
        ChatOptions options = prompt.getOptions();
        if (options instanceof ToolCallingChatOptions toolOptions) {
            Map<String, Object> ctx = toolOptions.getToolContext();
            if (ctx != null) {
                Object value = ctx.get(key);
                if (type.isInstance(value)) {
                    return (T) value;
                }
            }
        }
        return null;
    }

    private ToolExecutionResult executeSafely(Prompt prompt, ChatResponse chatResponse) {
        try {
            return CompletableFuture.supplyAsync(() -> delegate.executeToolCalls(prompt, chatResponse))
                    .orTimeout(toolTimeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.warn("[MaxToolCallManager] 工具执行超时 ({}ms)", toolTimeoutMs);
                String timeoutMsg = "工具调用超时(" + (toolTimeoutMs / 1000) + "秒)，请基于已有信息回答用户。";
                return buildErrorResponse(chatResponse, timeoutMsg);
            }
            log.warn("[MaxToolCallManager] 工具执行异常: {}", e.getMessage());
            String errorMsg = "工具调用失败: " + e.getMessage();
            return buildErrorResponse(chatResponse, errorMsg);
        } catch (Exception e) {
            log.warn("[MaxToolCallManager] 工具执行异常: {}", e.getMessage());
            String errorMsg = "工具调用失败: " + e.getMessage();
            return buildErrorResponse(chatResponse, errorMsg);
        }
    }

    private ToolExecutionResult buildErrorResponse(ChatResponse chatResponse, String errorMsg) {
        AssistantMessage originalAssistant = findAssistantWithToolCalls(chatResponse);
        List<AssistantMessage.ToolCall> toolCalls = originalAssistant.getToolCalls();

        AssistantMessage safeAssistant = new AssistantMessage(
                originalAssistant.getText() != null ? originalAssistant.getText() : "",
                originalAssistant.getMetadata() != null ? originalAssistant.getMetadata() : Map.of(),
                toolCalls
        );

        List<Message> errorHistory = new ArrayList<>();
        errorHistory.add(safeAssistant);
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : toolCalls) {
            responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), errorMsg));
        }
        errorHistory.add(new ToolResponseMessage(responses));
        return new CachedToolExecutionResult(errorHistory);
    }

    private AssistantMessage findAssistantWithToolCalls(ChatResponse chatResponse) {
        return chatResponse.getResults().stream()
                .map(generation -> (AssistantMessage) generation.getOutput())
                .filter(assistant -> assistant != null && !assistant.getToolCalls().isEmpty())
                .findFirst()
                .orElseGet(() -> chatResponse.getResult().getOutput());
    }

    private String extractResultText(ToolExecutionResult result) {
        List<Message> history = result.conversationHistory();
        if (history == null || history.isEmpty()) return "";
        Message last = history.get(history.size() - 1);
        if (last instanceof ToolResponseMessage toolMsg) {
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse r : toolMsg.getResponses()) {
                sb.append(r.responseData());
            }
            return sb.toString();
        }
        return last.getText() != null ? last.getText() : "";
    }

    private boolean isFetchTool(String toolName) {
        return FETCH_TOOL_NAMES.contains(toolName);
    }

    private boolean isSearchTool(String toolName) {
        return SEARCH_TOOL_NAMES.contains(toolName);
    }

    private boolean isNonRetriableError(String resultText) {
        if (resultText == null) return false;
        return resultText.contains("\"retriable\":false")
                || resultText.contains("\"retriable\": false")
                || resultText.contains("\"retriable\":  false");
    }

    private boolean isAStockTool(String toolName) {
        return toolName.startsWith("a_stock_") || toolName.equals("market_data");
    }

    private java.util.Set<String> extractStockCodesFromArgs(String args) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        if (args == null || args.isBlank()) return codes;

        // 提取 JSON 中的 stockCode 和 codes 字段值
        try {
            // 匹配 "stockCode":"600519" 或 "codes":"600519,000858" 格式
            java.util.regex.Pattern stockCodePattern = java.util.regex.Pattern.compile(
                    "\"(?:stockCode|codes|stockCodes)\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = stockCodePattern.matcher(args);
            while (matcher.find()) {
                String value = matcher.group(1);
                // 处理逗号分隔的多个代码
                for (String part : value.split(",")) {
                    String trimmed = part.trim();
                    if (trimmed.matches("\\d{6}")) {
                        codes.add(trimmed);
                    } else if (trimmed.matches("(?i)(sh|sz|bj)\\d{6}")) {
                        codes.add(trimmed.substring(2));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[MaxToolCallManager] 无法从参数中提取股票代码: {}", args);
        }
        return codes;
    }

    private String extractUrlFromArgs(String args) {
        if (args == null || args.isBlank()) return null;
        try {
            int urlIdx = args.indexOf("\"url\"");
            if (urlIdx < 0) return null;
            int colonIdx = args.indexOf(':', urlIdx);
            if (colonIdx < 0) return null;
            int startQuote = args.indexOf('"', colonIdx + 1);
            if (startQuote < 0) return null;
            int endQuote = args.indexOf('"', startQuote + 1);
            if (endQuote < 0) return null;
            return args.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            log.debug("[MaxToolCallManager] 无法从参数中提取URL: {}", args);
            return null;
        }
    }

    private List<Message> buildSkipResult(AssistantMessage.ToolCall tc, String skipMsg) {
        List<Message> history = new ArrayList<>();
        history.add(new AssistantMessage("", Map.of(), List.of(tc)));
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), skipMsg));
        history.add(new ToolResponseMessage(responses));
        return history;
    }

    private record CachedToolExecutionResult(List<Message> conversationHistory) implements ToolExecutionResult {
    }

    /**
     * 为 AStock 工具自动注入当前日期，防止 LLM 传入错误日期或使用训练数据中的旧日期。
     * - AStockLimitUpRouterTool: date 参数，格式 yyyyMMdd
     * - AStockSignalRouterTool: tradeDate 参数，格式 yyyy-MM-dd
     * - AStockReportRouterTool: industryReport 的 reportDate 参数
     */
    private String injectCurrentDate(String args, String toolName) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode root;
            if (args == null || args.isBlank()) {
                root = mapper.createObjectNode();
            } else {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(args);
                if (node.isObject()) {
                    root = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                } else {
                    return args;
                }
            }

            String todayCompact = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String todayDash = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // AStockLimitUpRouterTool: date 格式 yyyyMMdd
            if ("a_stock_limit_up".equals(toolName)) {
                root.put("date", todayCompact);
            }
            // AStockSignalRouterTool: tradeDate 格式 yyyy-MM-dd
            if ("a_stock_signal".equals(toolName)) {
                root.put("tradeDate", todayDash);
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("[MaxToolCallManager] 注入当前日期失败: {}", e.getMessage());
            return args;
        }
    }

    /**
     * 当 AStock 工具调用缺少 stockCode/stockCodes 参数时，自动注入目标代码。
     * 避免工具因缺少参数报错，导致 LLM fallback 到训练知识。
     */
    private String injectStockCode(String args, String toolName, String targetCode) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode root;
            if (args == null || args.isBlank()) {
                root = mapper.createObjectNode();
            } else {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(args);
                if (node.isObject()) {
                    root = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                } else {
                    return args;
                }
            }
            // 根据工具类型注入不同的参数名
            if ("a_stock_quote".equals(toolName)) {
                if (!root.has("stockCodes") || root.get("stockCodes").asText("").isBlank()) {
                    root.put("stockCodes", targetCode);
                }
            } else {
                if (!root.has("stockCode") || root.get("stockCode").asText("").isBlank()) {
                    root.put("stockCode", targetCode);
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("[MaxToolCallManager] 注入 stockCode 失败: {}", e.getMessage());
            return args;
        }
    }

    /**
     * 在工具返回结果前面追加标识头
     */
    private ToolExecutionResult prependToResult(ToolExecutionResult original,
                                                 AssistantMessage.ToolCall tc, String header) {
        List<Message> history = new ArrayList<>(original.conversationHistory());
        if (!history.isEmpty()) {
            Message last = history.get(history.size() - 1);
            if (last instanceof ToolResponseMessage toolMsg) {
                List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse r : toolMsg.getResponses()) {
                    newResponses.add(new ToolResponseMessage.ToolResponse(
                            r.id(), r.name(), header + r.responseData()));
                }
                history.set(history.size() - 1, new ToolResponseMessage(newResponses));
            }
        }
        return new CachedToolExecutionResult(history);
    }

    private ToolExecutionResult wrapToResult(ToolExecutionResult original,
                                              AssistantMessage.ToolCall tc, String header, String footer) {
        List<Message> history = new ArrayList<>(original.conversationHistory());
        if (!history.isEmpty()) {
            Message last = history.get(history.size() - 1);
            if (last instanceof ToolResponseMessage toolMsg) {
                List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse r : toolMsg.getResponses()) {
                    newResponses.add(new ToolResponseMessage.ToolResponse(
                            r.id(), r.name(), header + r.responseData() + footer));
                }
                history.set(history.size() - 1, new ToolResponseMessage(newResponses));
            }
        }
        return new CachedToolExecutionResult(history);
    }

    /**
     * 从工具返回文本中识别外来股票代码，过滤掉允许范围内的。
     * 只识别带 sh/sz/bj 前缀或紧跟中文股票名的代码，避免把资金流数值误判为股票代码。
     */
    private java.util.Set<String> extractForeignStockCodes(String text, java.util.Set<String> allowedCodes) {
        java.util.Set<String> foreign = new java.util.HashSet<>();
        if (text == null || text.isBlank()) return foreign;

        // 模式1: sh600487 / sz000858 / bj830799 带交易所前缀
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(?i)(?:sh|sz|bj)(\\d{6})").matcher(text);
        while (m1.find()) {
            String code = m1.group(1);
            if (!allowedCodes.contains(code)) {
                foreign.add(code);
            }
        }

        // 模式2: 6位数字后紧跟中文字符（股票名称），如 "601869长飞光纤"
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(\\d{6})[\\u4e00-\\u9fff]").matcher(text);
        while (m2.find()) {
            String code = m2.group(1);
            if (isValidAShareCode(code) && !allowedCodes.contains(code)) {
                foreign.add(code);
            }
        }

        return foreign;
    }

    /**
     * 判断是否为合法的A股代码格式（0/3/6 开头的6位数字）
     */
    private boolean isValidAShareCode(String code) {
        return code.startsWith("0") || code.startsWith("3") || code.startsWith("6");
    }

    /**
     * 硬过滤工具返回结果：将外来股票代码及关联的股票名替换为占位符，保留行内其他有用数据。
     */
    private ToolExecutionResult filterToolResult(ToolExecutionResult original,
                                                  AssistantMessage.ToolCall tc,
                                                  String resultText,
                                                  java.util.Set<String> allowedCodes,
                                                  java.util.Set<String> foreignCodes) {
        String filtered = resultText;
        int replacementCount = 0;
        for (String code : foreignCodes) {
            // 替换 "代码+中文股票名" 模式，如 "601869长飞光纤" → "[非目标标的]"
            String codeNamePattern = code + "[\\u4e00-\\u9fff]{2,8}";
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(codeNamePattern).matcher(filtered);
            while (m1.find()) replacementCount++;
            filtered = filtered.replaceAll(codeNamePattern, "[非目标标的]");

            // 替换 "sh/sz/bj+代码" 模式
            String prefixPattern = "(?i)(?:sh|sz|bj)" + code;
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(prefixPattern).matcher(filtered);
            while (m2.find()) replacementCount++;
            filtered = filtered.replaceAll(prefixPattern, "[非目标标的]");

            // 替换孤立的合法代码（仅限 0/3/6 开头）
            if (isValidAShareCode(code)) {
                java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("\\b" + code + "\\b").matcher(filtered);
                while (m3.find()) replacementCount++;
                filtered = filtered.replaceAll("\\b" + code + "\\b", "[非目标标的]");
            }
        }
        if (replacementCount > 0) {
            filtered += "\n\n[已替换 " + replacementCount + " 处非目标标的数据，当前分析标的为 " + allowedCodes + "]";
        }
        log.info("[MaxToolCallManager] 硬过滤完成: 替换 {} 处外来数据", replacementCount);

        List<Message> history = new ArrayList<>(original.conversationHistory());
        if (!history.isEmpty()) {
            Message last = history.get(history.size() - 1);
            if (last instanceof ToolResponseMessage toolMsg) {
                List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse r : toolMsg.getResponses()) {
                    newResponses.add(new ToolResponseMessage.ToolResponse(
                            r.id(), r.name(), filtered));
                }
                history.set(history.size() - 1, new ToolResponseMessage(newResponses));
            }
        }
        return new CachedToolExecutionResult(history);
    }

    private static class GuardedToolExecutionResult implements ToolExecutionResult {

        private final List<Message> guardedHistory;

        GuardedToolExecutionResult(ToolExecutionResult delegate, GuardSignal signal) {
            List<Message> original = delegate.conversationHistory();
            this.guardedHistory = injectAsSeparateMessage(original, signal);
        }

        private static List<Message> injectAsSeparateMessage(List<Message> messages, GuardSignal signal) {
            List<Message> result = new ArrayList<>(messages.size() + 2);
            result.addAll(messages);
            // 先插入匹配的 tool_use，满足 Anthropic API 协议要求
            AssistantMessage.ToolCall guardToolCall = new AssistantMessage.ToolCall(
                    "__guard_signal__", "function", "__guard_signal__", "{}");
            result.add(new AssistantMessage("", Map.of(), List.of(guardToolCall)));
            // 再插入 tool_result
            List<ToolResponseMessage.ToolResponse> signalResponses = new ArrayList<>();
            signalResponses.add(new ToolResponseMessage.ToolResponse(
                    "__guard_signal__", "__guard_signal__", signal.format()));
            result.add(new ToolResponseMessage(signalResponses));
            return result;
        }

        @Override
        public List<Message> conversationHistory() {
            return guardedHistory;
        }

        @Override
        public boolean returnDirect() {
            // 不再强制返回，让 AI 模型有机会基于工具数据生成完整报告
            return false;
        }
    }
}
