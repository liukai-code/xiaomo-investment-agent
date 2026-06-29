package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.tool.guard.FetchSessionTracker;
import com.itlk.myclaudecode.tool.guard.GuardSignal;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MaxToolCallManager implements ToolCallingManager {

    public static final String TOOL_CALL_COUNTER_KEY = "toolCallCounter";
    public static final String INFO_GAIN_TRACKER_KEY = "infoGainTracker";
    public static final String REPETITION_DETECTOR_KEY = "repetitionDetector";
    public static final String FETCH_SESSION_TRACKER_KEY = "fetchSessionTracker";
    public static final String SEARCH_SESSION_TRACKER_KEY = "searchSessionTracker";

    private static final Set<String> FETCH_TOOL_NAMES = Set.of(
            "fetchArticleContent", "fetchWebpage"
    );

    private static final Set<String> SEARCH_TOOL_NAMES = Set.of(
            "bailian_web_search", "webSearch", "web_search"
    );

    private final ToolCallingManager delegate;
    private final ToolGuardProperties properties;
    private final ToolBehaviorRegistry behaviorRegistry;

    private final ThreadLocal<Map<String, List<Message>>> duplicateCache =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                    return size() > 50;
                }
            });

    public MaxToolCallManager(
            ToolCallbackResolver toolCallbackResolver,
            ToolExecutionExceptionProcessor exceptionProcessor,
            ToolGuardProperties properties,
            ToolBehaviorRegistry behaviorRegistry) {
        this.properties = properties;
        this.behaviorRegistry = behaviorRegistry;
        this.delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver)
                .toolExecutionExceptionProcessor(exceptionProcessor)
                .build();
        log.info("MaxToolCallManager V2 初始化, softLimit={}, hardLimit={}", properties.softLimit(), properties.maxIterations());
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        AtomicInteger counter = extractFromContext(prompt, TOOL_CALL_COUNTER_KEY, AtomicInteger.class);
        InfoGainTracker infoGainTracker = extractFromContext(prompt, INFO_GAIN_TRACKER_KEY, InfoGainTracker.class);
        RepetitionDetector repetitionDetector = extractFromContext(prompt, REPETITION_DETECTOR_KEY, RepetitionDetector.class);
        FetchSessionTracker fetchTracker = extractFromContext(prompt, FETCH_SESSION_TRACKER_KEY, FetchSessionTracker.class);
        SearchSessionTracker searchTracker = extractFromContext(prompt, SEARCH_SESSION_TRACKER_KEY, SearchSessionTracker.class);

        int step = counter.incrementAndGet();
        AssistantMessage assistantWithTools = findAssistantWithToolCalls(chatResponse);
        List<AssistantMessage.ToolCall> toolCalls = assistantWithTools.getToolCalls();
        String toolNames = toolCalls.stream().map(AssistantMessage.ToolCall::name).collect(java.util.stream.Collectors.joining(", "));
        log.info("[MaxToolCallManager] 工具调用轮次: {}/{}, 工具: [{}]", step, properties.maxIterations(), toolNames);
        ToolExecutionResult result;

        if (toolCalls.size() == 1) {
            AssistantMessage.ToolCall tc = toolCalls.get(0);
            boolean cacheable = behaviorRegistry.getBehavior(tc.name()).cacheable();
            boolean isFetchTool = isFetchTool(tc.name());

            // URL 去重检查（执行前拦截）
            if (isFetchTool && fetchTracker != null) {
                String url = extractUrlFromArgs(tc.arguments());
                if (url != null && fetchTracker.isUrlVisited(url)) {
                    log.info("[MaxToolCallManager] 检测到重复URL，跳过抓取: {}", url);
                    String skipMsg = "该URL已在本次会话中抓取过，请使用之前的搜索结果。已抓取URL数: " + fetchTracker.getVisitedUrlCount();
                    List<Message> skipHistory = buildSkipResult(tc, skipMsg);
                    InfoGainLevel infoGain = infoGainTracker.recordAndGetLevel(skipMsg);
                    RepetitionResult repetition = repetitionDetector.recordAndDetect(tc.name(), tc.arguments());

                    GuardSignal signal = new GuardSignal(
                            step, properties.softLimit(), properties.maxIterations(),
                            infoGain, infoGainTracker.getLastSimilarity(),
                            repetition, tc.name(),
                            true, fetchTracker.getFetchCount(), true,
                            0, false, false);
                    return new GuardedToolExecutionResult(new CachedToolExecutionResult(skipHistory), signal);
                }
            }

            // 正常执行
            if (cacheable) {
                String cacheKey = tc.name() + ":" + tc.arguments().hashCode();
                List<Message> cachedResult = duplicateCache.get().get(cacheKey);
                if (cachedResult != null) {
                    log.info("[MaxToolCallManager] 检测到重复工具调用，返回缓存结果: {}", tc.name());
                    return new CachedToolExecutionResult(cachedResult);
                }
                result = executeSafely(prompt, chatResponse);
                duplicateCache.get().put(cacheKey, result.conversationHistory());
            } else {
                result = executeSafely(prompt, chatResponse);
            }

            // 信息增益和重复检测
            String resultText = extractResultText(result);
            InfoGainLevel infoGain = infoGainTracker.recordAndGetLevel(resultText);
            RepetitionResult repetition = repetitionDetector.recordAndDetect(tc.name(), tc.arguments());

            // 搜索工具检测：限制搜索轮次 + 注入规划指令
            if (isSearchTool(tc.name()) && searchTracker != null) {
                SearchSessionTracker.SearchResult sr = searchTracker.recordSearch();
                if (sr.isOverLimit()) {
                    log.info("[MaxToolCallManager] 搜索次数超限({}/{}), 拒绝搜索", sr.totalSearches(), properties.maxSearchRounds());
                    String limitMsg = "本次会话搜索次数已达上限。请立即停止所有工具调用，基于已有的搜索结果直接回答用户。不要再调用搜索或抓取工具。";
                    List<Message> limitHistory = buildSkipResult(tc, limitMsg);
                    GuardSignal signal = new GuardSignal(
                            step, properties.softLimit(), properties.maxIterations(),
                            infoGain, infoGainTracker.getLastSimilarity(),
                            repetition, tc.name(),
                            false, 0, false, 0, false, false);
                    return new GuardedToolExecutionResult(new CachedToolExecutionResult(limitHistory), signal);
                }
            }

            // Fetch 专项追踪
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
                    step, properties.softLimit(), properties.maxIterations(),
                    infoGain, infoGainTracker.getLastSimilarity(),
                    repetition, tc.name(),
                    isFetchTool, fetchCount, isDuplicateUrl,
                    consecutiveNoNewInfo, overMaxFetches, stuckNoNewInfo);

            log.info("[MaxToolCallManager] 信号: infoGain={}, repetition={}, shouldInject={}, fetch={}",
                    infoGain, repetition, signal.shouldInject(), isFetchTool);

            if (signal.isHardLimit()) {
                if (overMaxFetches) {
                    log.warn("[MaxToolCallManager] 达到 fetch 硬上限 (fetchCount={}), 强制停止", fetchCount);
                } else {
                    log.warn("[MaxToolCallManager] 达到迭代硬上限 ({}/{}), 强制停止", step, properties.maxIterations());
                }
                return new GuardedToolExecutionResult(result, signal);
            }

            if (signal.shouldInject()) {
                counter.set(0);
                return new GuardedToolExecutionResult(result, signal);
            }
        } else {
            result = executeSafely(prompt, chatResponse);
        }

        return result;
    }

    public void reset() {
        duplicateCache.get().clear();
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
            return delegate.executeToolCalls(prompt, chatResponse);
        } catch (Exception e) {
            log.warn("[MaxToolCallManager] 工具执行异常，返回错误信息给模型: {}", e.getMessage());
            String errorMsg = "工具调用失败: " + e.getMessage();
            AssistantMessage originalAssistant = findAssistantWithToolCalls(chatResponse);
            List<AssistantMessage.ToolCall> toolCalls = originalAssistant.getToolCalls();

            // 显式重建 AssistantMessage，确保 toolCalls 不会因序列化丢失
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

    private static class GuardedToolExecutionResult implements ToolExecutionResult {

        private final List<Message> guardedHistory;
        private final boolean hardLimit;

        GuardedToolExecutionResult(ToolExecutionResult delegate, GuardSignal signal) {
            this.hardLimit = signal.isHardLimit();
            List<Message> original = delegate.conversationHistory();
            this.guardedHistory = appendSignal(original, signal.format());
        }

        private static List<Message> appendSignal(List<Message> messages, String signal) {
            List<Message> result = new ArrayList<>(messages.size());
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (i == messages.size() - 1 && msg instanceof ToolResponseMessage toolMsg) {
                    List<ToolResponseMessage.ToolResponse> signaled = toolMsg.getResponses()
                            .stream()
                            .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.responseData() + signal))
                            .toList();
                    result.add(new ToolResponseMessage(signaled, msg.getMetadata()));
                } else {
                    result.add(msg);
                }
            }
            return result;
        }

        @Override
        public List<Message> conversationHistory() {
            return guardedHistory;
        }

        @Override
        public boolean returnDirect() {
            return hardLimit;
        }
    }
}
