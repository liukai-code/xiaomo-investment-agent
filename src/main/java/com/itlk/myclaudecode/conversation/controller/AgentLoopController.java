package com.itlk.myclaudecode.conversation.controller;

import com.itlk.myclaudecode.agent.service.AgentLoop;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.conversation.entity.ChatMessage;
import com.itlk.myclaudecode.conversation.entity.Conversation;
import com.itlk.myclaudecode.conversation.service.ChatMessageService;
import com.itlk.myclaudecode.conversation.service.ConversationService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    public Flux<String> chatStream(
            @RequestParam Long conversationId,
            @RequestParam String message,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        log.info("收到流式消息：userId={}, conversationId={}, message={}", userId, conversationId, message);
        return agentLoop.chatStream(userId, conversationId, message);
    }
}
