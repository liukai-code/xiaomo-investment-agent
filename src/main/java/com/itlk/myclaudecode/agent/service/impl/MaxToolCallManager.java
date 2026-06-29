package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.tool.guard.GuardSignal;
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
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MaxToolCallManager implements ToolCallingManager {

    public static final String TOOL_CALL_COUNTER_KEY = "toolCallCounter";
    public static final String INFO_GAIN_TRACKER_KEY = "infoGainTracker";
    public static final String REPETITION_DETECTOR_KEY = "repetitionDetector";

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

        int step = counter.incrementAndGet();
        AssistantMessage assistantWithTools = findAssistantWithToolCalls(chatResponse);
        List<AssistantMessage.ToolCall> toolCalls = assistantWithTools.getToolCalls();
        String toolNames = toolCalls.stream().map(AssistantMessage.ToolCall::name).collect(java.util.stream.Collectors.joining(", "));
        log.info("[MaxToolCallManager] 工具调用轮次: {}/{}, 工具: [{}]", step, properties.maxIterations(), toolNames);
        ToolExecutionResult result;

        if (toolCalls.size() == 1) {
            AssistantMessage.ToolCall tc = toolCalls.get(0);
            boolean cacheable = behaviorRegistry.getBehavior(tc.name()).cacheable();

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

            // Record for info gain and repetition detection
            String resultText = extractResultText(result);
            InfoGainLevel infoGain = infoGainTracker.recordAndGetLevel(resultText);
            RepetitionResult repetition = repetitionDetector.recordAndDetect(tc.name(), tc.arguments());

            GuardSignal signal = new GuardSignal(
                    step, properties.softLimit(), properties.maxIterations(),
                    infoGain, infoGainTracker.getLastSimilarity(),
                    repetition, tc.name());

            log.info("[MaxToolCallManager] 信号: infoGain={}, repetition={}, shouldInject={}",
                    infoGain, repetition, signal.shouldInject());

            if (signal.isHardLimit()) {
                log.warn("[MaxToolCallManager] 达到硬上限 ({}/{}), 强制停止", step, properties.maxIterations());
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

    private record CachedToolExecutionResult(List<Message> conversationHistory) implements ToolExecutionResult {
    }

    private static class GuardedToolExecutionResult implements ToolExecutionResult {

        private final List<Message> guardedHistory;
        private final boolean hardLimit;

        GuardedToolExecutionResult(ToolExecutionResult delegate, GuardSignal signal) {
            this.hardLimit = signal.isHardLimit();
            String signalText = signal.format();
            List<Message> original = delegate.conversationHistory();
            this.guardedHistory = appendSignal(original, signalText);
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
