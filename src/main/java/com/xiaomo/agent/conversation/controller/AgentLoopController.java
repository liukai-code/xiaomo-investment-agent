package com.xiaomo.agent.conversation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.agent.service.AgentLoop;
import com.xiaomo.agent.common.entity.Result;
import com.xiaomo.agent.conversation.entity.ChatMessage;
import com.xiaomo.agent.conversation.entity.Conversation;
import com.xiaomo.agent.conversation.entity.MessageRole;
import com.xiaomo.agent.conversation.service.ChatMessageService;
import com.xiaomo.agent.conversation.service.ConversationService;
import com.xiaomo.agent.workflow.service.DeepAnalysisWorkflow;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/agent")
@Slf4j
public class AgentLoopController {

    @Resource
    private AgentLoop agentLoop;

    @Resource
    private ConversationService conversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private DeepAnalysisWorkflow deepAnalysisWorkflow;

    @Resource
    private ObjectMapper objectMapper;

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        return userId;
    }

    // ========== 会话管理 ==========

    @PostMapping("/conversation")
    public Result<Conversation> createConversation(
            @RequestParam(required = false) String title,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        Conversation conversation = conversationService.createConversation(
                userId, title != null ? title : "新对话");
        return Result.success(conversation);
    }

    @GetMapping("/conversation/list")
    public Result<List<Conversation>> listConversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(conversationService.listConversations(userId));
    }

    @GetMapping("/conversation/{id}/messages")
    public Result<List<ChatMessage>> getHistory(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(chatMessageService.getHistory(userId, id));
    }

    @DeleteMapping("/conversation/{id}")
    public Result<Void> deleteConversation(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        conversationService.deleteConversation(userId, id);
        return Result.success(null);
    }

    @PostMapping("/conversation/{id}/generate-title")
    public Result<String> generateTitle(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String title = agentLoop.generateTitle(userId, id);
        return Result.success(title);
    }

    @PostMapping("/conversation/{id}/message")
    public Result<Void> saveMessage(
            @PathVariable Long id,
            @RequestBody String content,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        chatMessageService.saveAssistantMessage(userId, id, content);
        return Result.success(null);
    }

    // ========== 聊天 ==========

    @GetMapping("/chat")
    public Result<String> chat(
            @RequestParam Long conversationId,
            @RequestParam String message,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        log.info("收到消息：userId={}, conversationId={}, message={}", userId, conversationId, message);
        String reply = agentLoop.chat(userId, conversationId, message);
        log.info("回复用户：{}", reply);
        return Result.success(reply);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam Long conversationId,
            @RequestParam String message,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        log.info("收到流式消息：userId={}, conversationId={}, message={}", userId, conversationId, message);
        return agentLoop.chatStream(userId, conversationId, message);
    }

    // ========== 深度分析工作流 ==========

    @GetMapping(value = "/chat/deep-analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> deepAnalysis(
            @RequestParam Long conversationId,
            @RequestParam String message,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        log.info("收到深度分析请求：userId={}, conversationId={}, message={}", userId, conversationId, message);

        // 保存用户消息
        Conversation conversation = conversationService.getConversationForUser(conversationId, userId);
        chatMessageService.saveMessage(conversation, MessageRole.USER, message, null, null);

        return deepAnalysisWorkflow.execute(userId, conversationId, message)
                .map(event -> {
                    try {
                        return ServerSentEvent.<String>builder()
                                .event("workflow")
                                .data(objectMapper.writeValueAsString(event))
                                .build();
                    } catch (JsonProcessingException e) {
                        return ServerSentEvent.<String>builder()
                                .event("error")
                                .data("{\"error\":\"序列化失败\"}")
                                .build();
                    }
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{\"conversationId\":" + conversationId + "}")
                                .build()
                ))
                .doOnComplete(() -> {
                    // 保存 AI 最终结果到 chat_messages
                    String summary = deepAnalysisWorkflow.buildSummaryForConversation(conversationId);
                    chatMessageService.saveMessage(conversation, MessageRole.ASSISTANT, summary, null, null);
                    log.info("深度分析结果已保存到会话 {}", conversationId);
                });
    }
}
