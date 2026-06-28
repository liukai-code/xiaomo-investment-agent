package com.itlk.myclaudecode.agent.service.impl;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MaxToolCallManager implements ToolCallingManager {

    public static final String TOOL_CALL_COUNTER_KEY = "toolCallCounter";

    private final ToolCallingManager delegate;
    private final int maxIterations;

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
            @Value("${agent.tool.max-iterations:10}") int maxIterations) {
        this.maxIterations = maxIterations;
        this.delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver)
                .toolExecutionExceptionProcessor(exceptionProcessor)
                .build();
        log.info("MaxToolCallManager 初始化, maxIterations={}", maxIterations);
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        AtomicInteger counter = extractCounter(prompt);
        int iteration = counter.incrementAndGet();
        log.info("[MaxToolCallManager] 工具调用轮次: {}/{}", iteration, maxIterations);

        List<AssistantMessage.ToolCall> toolCalls = chatResponse.getResult().getOutput().getToolCalls();
        ToolExecutionResult result;

        if (toolCalls.size() == 1) {
            AssistantMessage.ToolCall tc = toolCalls.get(0);
            String cacheKey = tc.name() + ":" + tc.arguments().hashCode();
            List<Message> cachedResult = duplicateCache.get().get(cacheKey);

            if (cachedResult != null) {
                log.info("[MaxToolCallManager] 检测到重复工具调用，返回缓存结果: {}", tc.name());
                return new CachedToolExecutionResult(cachedResult);
            }

            result = delegate.executeToolCalls(prompt, chatResponse);
            duplicateCache.get().put(cacheKey, result.conversationHistory());
        } else {
            result = delegate.executeToolCalls(prompt, chatResponse);
        }

        if (iteration >= maxIterations) {
            log.warn("[MaxToolCallManager] 工具调用已达软上限 ({}/{}), 提示模型自行判断", iteration, maxIterations);
            counter.set(0);
            return new WarnedToolExecutionResult(result, maxIterations);
        }

        return result;
    }

    public void reset() {
        duplicateCache.get().clear();
    }

    private AtomicInteger extractCounter(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options instanceof ToolCallingChatOptions toolOptions) {
            Map<String, Object> ctx = toolOptions.getToolContext();
            if (ctx != null) {
                Object counter = ctx.get(TOOL_CALL_COUNTER_KEY);
                if (counter instanceof AtomicInteger atomicCounter) {
                    return atomicCounter;
                }
            }
        }
        log.warn("[MaxToolCallManager] toolContext 中未找到计数器，使用 fallback ThreadLocal");
        return new AtomicInteger(0);
    }

    private record CachedToolExecutionResult(List<Message> conversationHistory) implements ToolExecutionResult {
    }

    private static class WarnedToolExecutionResult implements ToolExecutionResult {

        private static final String WARNING_TEMPLATE =
                "\n\n[系统提示] 你已在本轮对话中调用了 %d 轮工具。请基于已有结果综合分析，判断是否已获得足够信息来回答用户。"
                + "如果信息充足，请直接回答；如果仍需补充，请继续调用工具。";

        private final ToolExecutionResult delegate;
        private final List<Message> warnedHistory;

        WarnedToolExecutionResult(ToolExecutionResult delegate, int maxIterations) {
            this.delegate = delegate;
            String warning = String.format(WARNING_TEMPLATE, maxIterations);
            List<Message> original = delegate.conversationHistory();
            this.warnedHistory = appendWarning(original, warning);
        }

        private static List<Message> appendWarning(List<Message> messages, String warning) {
            List<Message> result = new java.util.ArrayList<>(messages.size());
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (i == messages.size() - 1 && msg instanceof ToolResponseMessage toolMsg) {
                    List<ToolResponseMessage.ToolResponse> warnedResponses = toolMsg.getResponses()
                            .stream()
                            .map(r -> new ToolResponseMessage.ToolResponse(
                                    r.id(), r.name(), r.responseData() + warning))
                            .toList();
                    result.add(new ToolResponseMessage(warnedResponses, msg.getMetadata()));
                } else {
                    result.add(msg);
                }
            }
            return result;
        }

        @Override
        public List<Message> conversationHistory() {
            return warnedHistory;
        }

        @Override
        public boolean returnDirect() {
            return false;
        }
    }
}
