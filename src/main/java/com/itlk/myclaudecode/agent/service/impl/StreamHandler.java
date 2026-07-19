package com.itlk.myclaudecode.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.agent.service.ChatStreamEvent;
import com.itlk.myclaudecode.conversation.service.ChatMessageService;
import com.itlk.myclaudecode.conversation.service.UsageRecordService;
import com.itlk.myclaudecode.user.service.FreeQuotaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 封装 SSE 流式事件组装逻辑（thinking → status ∥ content → done）。
 */
@Component
@Slf4j
public class StreamHandler {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private UsageRecordService usageRecordService;

    @Resource
    private FreeQuotaService freeQuotaService;

    public Flux<ServerSentEvent<String>> buildStream(ChatClient chatClient,
                                                      List<Message> context,
                                                      List<ToolCallback> enabledTools,
                                                      ChatOptions options,
                                                      Long convId, Long userId,
                                                      Map<String, Object> toolCtx,
                                                      Sinks.Many<ChatStreamEvent> statusSink,
                                                      boolean useFreeQuota) {
        StringBuilder accumulated = new StringBuilder();

        // 初始 THINKING 事件
        Flux<ServerSentEvent<String>> thinkingEvent = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("status")
                        .data(toJson(ChatStreamEvent.thinking()))
                        .build()
        );

        // 状态事件流（来自 MaxToolCallManager 的工具调用状态）
        Flux<ServerSentEvent<String>> statusEvents = statusSink.asFlux()
                .map(event -> ServerSentEvent.<String>builder()
                        .event("status")
                        .data(toJson(event))
                        .build());

        // Usage tracking accumulators
        final Long[] lastInputTokens = {null};
        final Long[] lastOutputTokens = {null};

        // 文本内容流 + done 事件
        Flux<ServerSentEvent<String>> contentWithDone = chatClient.prompt()
                .messages(context.toArray(new Message[0]))
                .toolCallbacks(enabledTools.toArray(new ToolCallback[0]))
                .options(options)
                .stream()
                .chatResponse()
                .map(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        String text = response.getResult().getOutput().getText();
                        if (text != null) {
                            accumulated.append(text);
                        }
                    }
                    // Capture usage from each response (last one wins)
                    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                        Usage usage = response.getMetadata().getUsage();
                        if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                            lastInputTokens[0] = usage.getPromptTokens().longValue();
                        }
                        if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
                            lastOutputTokens[0] = usage.getCompletionTokens().longValue();
                        }
                        log.debug("[ChatStream] chunk usage: input={}, output={}", usage.getPromptTokens(), usage.getCompletionTokens());
                    }
                    return ServerSentEvent.<String>builder()
                            .event("content")
                            .data(sanitizeOutput(accumulated.toString()))
                            .build();
                })
                // 内容流结束后关闭状态 sink 并发射 done 事件
                .doOnComplete(() -> statusSink.tryEmitComplete())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data(String.valueOf(convId))
                                .build()
                ));

        // 合并：thinking → (状态事件 ∥ 内容流+done) → 完成
        return Flux.concat(thinkingEvent, Flux.merge(statusEvents, contentWithDone))
                .doOnComplete(() -> {
                    String fullResponse = sanitizeOutput(accumulated.toString());
                    chatMessageService.saveAssistantMessage(userId, convId, fullResponse);
                    // Record token usage
                    try {
                        AtomicInteger toolCounter = (AtomicInteger) toolCtx.get(MaxToolCallManager.TOOL_CALL_COUNTER_KEY);
                        int toolCalls = toolCounter != null ? toolCounter.get() : 0;
                        Long inputTokens = lastInputTokens[0] != null ? lastInputTokens[0] : UsageRecordService.estimateInputTokens(context);
                        usageRecordService.record(userId, convId, inputTokens, lastOutputTokens[0], toolCalls);
                        log.info("[ChatStream] usage recorded: input={}, output={}, tools={}, useFreeQuota={}", inputTokens, lastOutputTokens[0], toolCalls, useFreeQuota);
                        // 免费额度扣减
                        if (useFreeQuota) {
                            long consumed = (inputTokens != null ? inputTokens : 0L) + (lastOutputTokens[0] != null ? lastOutputTokens[0] : 0L);
                            freeQuotaService.deduct(userId, consumed);
                        }
                    } catch (Exception e) {
                        log.warn("记录流式token用量失败: {}", e.getMessage());
                    }
                })
                .timeout(Duration.ofSeconds(300))
                .onErrorResume(e -> {
                    statusSink.tryEmitError(e);
                    String errorMsg = "服务端响应超时，请重试";
                    log.error("流式请求异常: {}", e.getMessage());
                    String partial = sanitizeOutput(accumulated.toString());
                    if (!partial.isEmpty()) {
                        chatMessageService.saveAssistantMessage(userId, convId, partial);
                    }
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("content")
                            .data("\n\n[" + errorMsg + "]")
                            .build());
                })
                .doFinally(signal -> {});
    }

    private String toJson(ChatStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("序列化 ChatStreamEvent 失败: {}", e.getMessage());
            return "{}";
        }
    }

    public static String sanitizeOutput(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\n*\\[GUARD:[\\s\\S]*?\\[/GUARD]\\n*", "")
                .replaceAll("\\n*\\[GUARD_SIGNAL\\][\\s\\S]*?\\[/GUARD_SIGNAL\\]\\n*", "")
                // 过滤 JSON 格式的工具调用
                .replaceAll("(?m)^\\s*\\{\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{[\\s\\S]*?\\}\\s*\\}\\s*$", "")
                // 过滤 XML 格式的工具调用（<function=...>...</function>）
                .replaceAll("(?s)\\n*<function=[^>]+>.*?</function>\\n*", "")
                // 过滤未闭合的 XML 工具调用（<function=...> + <parameter=...> 行）
                .replaceAll("(?m)^\\s*<function=[^>]+>\\s*$", "")
                .replaceAll("(?m)^\\s*<parameter=[^>]+>.*$", "")
                .trim();
    }
}
