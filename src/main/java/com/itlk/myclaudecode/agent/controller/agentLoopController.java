package com.itlk.myclaudecode.agent.controller;

import com.itlk.myclaudecode.agent.Entity.Result;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/agent")
@Slf4j
public class agentLoopController {

    @Resource
    private AgentLoop agentLoop;

    @GetMapping("/chat")
    public Result<String> chat(@RequestParam String message) {
        log.info("收到消息：{}",message);
        String reply = agentLoop.chat(message);
        log.info("回复用户：{}",reply);
        return Result.success(reply);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String message) {
        log.info("收到流式消息：{}", message);
        return agentLoop.chatStream(message);
    }

}
