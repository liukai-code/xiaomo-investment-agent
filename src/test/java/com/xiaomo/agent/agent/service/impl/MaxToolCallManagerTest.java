package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.ToolGuardProperties;
import com.xiaomo.agent.tool.guard.InfoGainTracker;
import com.xiaomo.agent.tool.guard.RepetitionDetector;
import com.xiaomo.agent.tool.guard.FetchSessionTracker;
import com.xiaomo.agent.tool.guard.SearchSessionTracker;
import com.xiaomo.agent.tool.guard.ToolBehaviorRegistry;
import com.xiaomo.agent.tool.guard.ReportCompletenessChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaxToolCallManager 工具守卫测试")
class MaxToolCallManagerTest {

    @Mock
    private ToolCallbackResolver toolCallbackResolver;

    @Mock
    private ToolExecutionExceptionProcessor exceptionProcessor;

    @Mock
    private ToolBehaviorRegistry behaviorRegistry;

    @Mock
    private org.springframework.ai.model.tool.ToolCallingManager delegateManager;

    private MaxToolCallManager manager;

    private static final int MAX_ITERATIONS = 5;
    private static final int MAX_FETCHES = 3;
    private static final int MAX_SAME_TOOL = 2;

    @BeforeEach
    void setUp() throws Exception {
        ToolGuardProperties properties = new ToolGuardProperties(
                MAX_ITERATIONS,  // maxIterations
                3,               // softLimit
                4,               // escalationWarning
                5,               // escalationFinal
                3,               // infoGainWindow
                0.8,             // infoGainThreshold
                3,               // repetitionThreshold
                MAX_FETCHES,     // maxFetches
                2,               // maxConsecutiveNoNewInfo
                3,               // maxSearchRounds
                60,              // toolTimeoutSeconds
                MAX_SAME_TOOL,   // maxSameToolCalls
                500,             // reportMinLength
                2                // reportMinSections
        );

        lenient().when(behaviorRegistry.getBehavior(anyString()))
                .thenReturn(new ToolBehaviorRegistry.ToolBehaviorData(true, false));

        manager = new MaxToolCallManager(toolCallbackResolver, exceptionProcessor, properties, behaviorRegistry);

        // 通过反射注入 mock delegate，避免真实 DefaultToolCallingManager 的依赖问题
        Field delegateField = MaxToolCallManager.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        delegateField.set(manager, delegateManager);

        // mock delegate 返回正常的工具执行结果
        lenient().when(delegateManager.executeToolCalls(any(), any())).thenAnswer(inv -> {
            Prompt p = inv.getArgument(0);
            ChatResponse cr = inv.getArgument(1);
            AssistantMessage assistant = findAssistantWithToolCalls(cr);
            List<Message> history = new ArrayList<>();
            history.add(assistant);
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
                responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), "mock result"));
            }
            history.add(new ToolResponseMessage(responses));
            return (ToolExecutionResult) () -> history;
        });
    }

    private AssistantMessage findAssistantWithToolCalls(ChatResponse chatResponse) {
        return chatResponse.getResults().stream()
                .map(g -> (AssistantMessage) g.getOutput())
                .filter(a -> a != null && !a.getToolCalls().isEmpty())
                .findFirst()
                .orElseGet(() -> chatResponse.getResult().getOutput());
    }

    // ========== 辅助方法 ==========

    /**
     * 构造包含 ToolCall 的 ChatResponse
     */
    private ChatResponse buildChatResponse(String toolName, String arguments) {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1", "function", toolName, arguments);
        AssistantMessage assistant = new AssistantMessage("", Map.of(), List.of(toolCall));
        Generation generation = new Generation(assistant);
        return new ChatResponse(List.of(generation));
    }

    /**
     * 构造包含多个 ToolCall 的 ChatResponse
     */
    private ChatResponse buildMultiToolResponse(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistant = new AssistantMessage("", Map.of(), toolCalls);
        Generation generation = new Generation(assistant);
        return new ChatResponse(List.of(generation));
    }

    /**
     * 构造带 toolContext 的 Prompt
     */
    private Prompt buildPromptWithContext(Map<String, Object> toolContext) {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolContext(toolContext)
                .build();
        return new Prompt("test", options);
    }

    /**
     * 构造基本的守卫上下文
     */
    private Map<String, Object> buildGuardContext(int step) {
        Map<String, Object> ctx = new ConcurrentHashMap<>();
        ctx.put(MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(step));
        ctx.put(MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(3, 0.8));
        ctx.put(MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(3));
        ctx.put(MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, new FetchSessionTracker(MAX_FETCHES, 2));
        ctx.put(MaxToolCallManager.SEARCH_SESSION_TRACKER_KEY, new SearchSessionTracker(3));
        ctx.put(MaxToolCallManager.DUPLICATE_CACHE_KEY, new ConcurrentHashMap<>());
        ctx.put(MaxToolCallManager.NON_RETRIABLE_CACHE_KEY, new ConcurrentHashMap<>());
        ctx.put(MaxToolCallManager.PER_TOOL_CALL_COUNT_KEY, new ConcurrentHashMap<String, AtomicInteger>());
        return ctx;
    }

    /**
     * 从结果中提取最后一个 ToolResponseMessage 的内容
     */
    private String extractLastResponseText(ToolExecutionResult result) {
        List<Message> history = result.conversationHistory();
        Message last = history.get(history.size() - 1);
        if (last instanceof ToolResponseMessage toolMsg) {
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse r : toolMsg.getResponses()) {
                sb.append(r.responseData());
            }
            return sb.toString();
        }
        return last.getText();
    }

    // ========== 迭代硬上限 ==========

    @Nested
    @DisplayName("iterationHardLimit 迭代硬上限")
    class IterationHardLimitTest {

        @Test
        @DisplayName("超过maxIterations → 阻止工具调用并返回GUARD消息")
        void overMaxIterations() {
            Map<String, Object> ctx = buildGuardContext(MAX_ITERATIONS); // step == max
            // incrementAndGet 后变成 max+1
            // 不设置 ALLOWED_STOCK_CODES_KEY（ConcurrentHashMap 不允许 null value）

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("a_stock_quote", "{\"stockCode\":\"600519\"}");

            ToolExecutionResult result = manager.executeToolCalls(prompt, response);

            String text = extractLastResponseText(result);
            assertTrue(text.contains("最大工具调用轮次限制") || text.contains("GUARD"),
                    "应包含守卫拦截信息，实际: " + text.substring(0, Math.min(100, text.length())));
        }
    }

    // ========== fetch 硬上限 ==========

    @Nested
    @DisplayName("fetchHardLimit 抓取硬上限")
    class FetchHardLimitTest {

        @Test
        @DisplayName("fetch超限且全部是fetch工具 → 阻止")
        void overFetchLimitAllFetch() {
            Map<String, Object> ctx = buildGuardContext(0);
            FetchSessionTracker fetchTracker = new FetchSessionTracker(MAX_FETCHES, 2);
            // 模拟已达到上限
            for (int i = 0; i < MAX_FETCHES + 1; i++) {
                fetchTracker.recordFetch("http://example.com/" + i, com.xiaomo.agent.tool.guard.InfoGainTracker.InfoGainLevel.HIGH);
            }
            ctx.put(MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, fetchTracker);

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("fetchWebpage", "{\"url\":\"http://example.com/new\"}");

            ToolExecutionResult result = manager.executeToolCalls(prompt, response);

            String text = extractLastResponseText(result);
            assertTrue(text.contains("抓取次数") || text.contains("GUARD") || text.contains("fetch"),
                    "应包含抓取限制信息");
        }
    }

    // ========== 同工具调用次数限制 ==========

    @Nested
    @DisplayName("perToolCallLimit 同工具调用次数限制")
    class PerToolCallLimitTest {

        @Test
        @DisplayName("同一工具调用超过上限 → 阻止")
        void overSameToolLimit() {
            Map<String, Object> ctx = buildGuardContext(1);
            Map<String, AtomicInteger> perTool = new ConcurrentHashMap<>();
            perTool.put("fetchWebpage", new AtomicInteger(MAX_SAME_TOOL)); // 已达上限
            ctx.put(MaxToolCallManager.PER_TOOL_CALL_COUNT_KEY, perTool);

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("fetchWebpage", "{\"url\":\"http://example.com\"}");

            ToolExecutionResult result = manager.executeToolCalls(prompt, response);

            String text = extractLastResponseText(result);
            assertTrue(text.contains("单工具调用上限") || text.contains("GUARD"),
                    "应包含单工具超限信息");
        }
    }

    // ========== 工具相关性守卫 ==========

    @Nested
    @DisplayName("toolRelevanceGuard 工具相关性守卫")
    class ToolRelevanceGuardTest {

        @Test
        @DisplayName("标的锁定时调用getMyHoldings → 被拦截")
        void blockHoldingsWhenTargetLocked() {
            Map<String, Object> ctx = buildGuardContext(1);
            ctx.put(MaxToolCallManager.ALLOWED_STOCK_CODES_KEY, Set.of("600519"));

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("getMyHoldings", "{}");

            ToolExecutionResult result = manager.executeToolCalls(prompt, response);

            String text = extractLastResponseText(result);
            assertTrue(text.contains("分析标的") || text.contains("专注于") || text.contains("getMyHoldings"),
                    "应拦截无关工具调用");
        }
    }

    // ========== 股票范围守卫 ==========

    @Nested
    @DisplayName("stockScopeGuard 股票范围守卫")
    class StockScopeGuardTest {

        @Test
        @DisplayName("错误股票代码 → 被拦截")
        void wrongStockCodeBlocked() {
            Map<String, Object> ctx = buildGuardContext(1);
            ctx.put(MaxToolCallManager.ALLOWED_STOCK_CODES_KEY, Set.of("600519"));

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("a_stock_quote",
                    "{\"stockCodes\":\"000858\",\"operation\":\"tencentQuote\"}");

            ToolExecutionResult result = manager.executeToolCalls(prompt, response);

            String text = extractLastResponseText(result);
            assertTrue(text.contains("不在分析范围内") || text.contains("目标股票"),
                    "应拦截错误股票代码");
        }

        @Test
        @DisplayName("缺少stockCode → 自动注入目标代码")
        void missingStockCodeAutoInject() {
            // 用 ArgumentCaptor 捕获传给 delegate 的 ChatResponse，验证注入结果
            ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
            reset(delegateManager);
            when(delegateManager.executeToolCalls(any(), responseCaptor.capture())).thenAnswer(inv -> {
                Prompt p = inv.getArgument(0);
                ChatResponse cr = inv.getArgument(1);
                AssistantMessage a = findAssistantWithToolCalls(cr);
                List<Message> history = new ArrayList<>();
                history.add(a);
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : a.getToolCalls()) {
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), "mock result"));
                }
                history.add(new ToolResponseMessage(responses));
                return (ToolExecutionResult) () -> history;
            });

            Map<String, Object> ctx = buildGuardContext(1);
            ctx.put(MaxToolCallManager.ALLOWED_STOCK_CODES_KEY, Set.of("600519"));

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("a_stock_signal",
                    "{\"operation\":\"conceptBlocks\"}");

            manager.executeToolCalls(prompt, response);

            // 验证 delegate 收到的 ChatResponse 中，ToolCall 参数已包含注入的 stockCode
            ChatResponse captured = responseCaptor.getValue();
            String injectedArgs = captured.getResult().getOutput().getToolCalls().get(0).arguments();
            assertTrue(injectedArgs.contains("600519"),
                    "应自动注入stockCode=600519，实际参数: " + injectedArgs);
        }
    }

    // ========== URL 去重 ==========

    @Nested
    @DisplayName("urlDedup URL去重")
    class UrlDedupTest {

        @Test
        @DisplayName("重复URL → 跳过抓取返回缓存消息")
        void duplicateUrlSkipped() {
            Map<String, Object> ctx = buildGuardContext(1);
            FetchSessionTracker fetchTracker = new FetchSessionTracker(10, 2);
            fetchTracker.recordFetch("http://example.com/page", com.xiaomo.agent.tool.guard.InfoGainTracker.InfoGainLevel.HIGH);
            ctx.put(MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, fetchTracker);

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("fetchWebpage",
                    "{\"url\":\"http://example.com/page\"}");

            ToolExecutionResult result = manager.executeToolCalls(prompt, response);

            String text = extractLastResponseText(result);
            assertTrue(text.contains("已抓取") || text.contains("已访问") || text.contains("URL"),
                    "应跳过重复URL");
        }
    }

    // ========== 日期自动注入 ==========

    @Nested
    @DisplayName("dateAutoInjection 日期自动注入")
    class DateAutoInjectionTest {

        @Test
        @DisplayName("a_stock_limit_up工具 → 自动注入date字段")
        void injectDateForLimitUp() {
            // 用 ArgumentCaptor 捕获传给 delegate 的 ChatResponse，验证日期注入
            ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
            reset(delegateManager);
            when(delegateManager.executeToolCalls(any(), responseCaptor.capture())).thenAnswer(inv -> {
                Prompt p = inv.getArgument(0);
                ChatResponse cr = inv.getArgument(1);
                AssistantMessage a = findAssistantWithToolCalls(cr);
                List<Message> history = new ArrayList<>();
                history.add(a);
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : a.getToolCalls()) {
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), "mock result"));
                }
                history.add(new ToolResponseMessage(responses));
                return (ToolExecutionResult) () -> history;
            });

            Map<String, Object> ctx = buildGuardContext(1);

            Prompt prompt = buildPromptWithContext(ctx);
            ChatResponse response = buildChatResponse("a_stock_limit_up",
                    "{\"operation\":\"ztPool\"}");

            manager.executeToolCalls(prompt, response);

            // 验证 delegate 收到的参数已包含今天的日期（yyyyMMdd 格式）
            ChatResponse captured = responseCaptor.getValue();
            String injectedArgs = captured.getResult().getOutput().getToolCalls().get(0).arguments();
            String today = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            assertTrue(injectedArgs.contains(today),
                    "应自动注入date=" + today + "，实际参数: " + injectedArgs);
        }
    }

    // ========== reset ==========

    @Nested
    @DisplayName("reset 重置")
    class ResetTest {

        @Test
        @DisplayName("reset → 不抛异常（no-op）")
        void resetNoOp() {
            assertDoesNotThrow(() -> manager.reset(), "reset不应抛异常");
        }
    }
}
