package com.itlk.myclaudecode.agent.controller;

import com.itlk.myclaudecode.agent.Entity.ChatMessage;
import com.itlk.myclaudecode.agent.Entity.Conversation;
import com.itlk.myclaudecode.agent.Entity.Result;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
@Slf4j
public class agentLoopController {

    @Resource
    private AgentLoop agentLoop;

    // ========== 会话管理 ==========

    @PostMapping("/conversation")
    public Result<Conversation> createConversation(@RequestParam(required = false) String title) {
        Conversation conversation = agentLoop.createConversation(title != null ? title : "新对话");
        return Result.success(conversation);
    }

    @GetMapping("/conversation/list")
    public Result<List<Conversation>> listConversations() {
        return Result.success(agentLoop.listConversations());
    }

    @GetMapping("/conversation/{id}/messages")
    public Result<List<ChatMessage>> getHistory(@PathVariable Long id) {
        return Result.success(agentLoop.getHistory(id));
    }

    // ========== 聊天 ==========

    @GetMapping("/chat")
    public Result<String> chat(@RequestParam Long conversationId, @RequestParam String message) {
        log.info("收到消息：conversationId={}, message={}", conversationId, message);
        String reply = agentLoop.chat(conversationId, message);
        log.info("回复用户：{}", reply);
        return Result.success(reply);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam Long conversationId, @RequestParam String message) {
        log.info("收到流式消息：conversationId={}, message={}", conversationId, message);
        return agentLoop.chatStream(conversationId, message);
    }
}
