package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.common.exception.ToolCallLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MaxToolCallManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final int maxIterations;

    private final ThreadLocal<Integer> currentIteration = ThreadLocal.withInitial(() -> 0);
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
        int iteration = currentIteration.get() + 1;
        currentIteration.set(iteration);
        log.info("[MaxToolCallManager] 工具调用轮次: {}/{}", iteration, maxIterations);

        if (iteration > maxIterations) {
            log.warn("[MaxToolCallManager] 工具调用轮次超限 ({}/{})", iteration, maxIterations);
            throw new ToolCallLimitExceededException(maxIterations);
        }

        List<AssistantMessage.ToolCall> toolCalls = chatResponse.getResult().getOutput().getToolCalls();
        if (toolCalls.size() == 1) {
            AssistantMessage.ToolCall tc = toolCalls.get(0);
            String cacheKey = tc.name() + ":" + tc.arguments().hashCode();
            List<Message> cachedResult = duplicateCache.get().get(cacheKey);

            if (cachedResult != null) {
                log.info("[MaxToolCallManager] 检测到重复工具调用，返回缓存结果: {}", tc.name());
                return new CachedToolExecutionResult(cachedResult);
            }

            ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
            duplicateCache.get().put(cacheKey, result.conversationHistory());
            return result;
        }

        return delegate.executeToolCalls(prompt, chatResponse);
    }

    public void reset() {
        currentIteration.set(0);
        duplicateCache.get().clear();
    }

    private record CachedToolExecutionResult(List<Message> conversationHistory) implements ToolExecutionResult {
    }
}
