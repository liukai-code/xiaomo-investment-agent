package com.itlk.myclaudecode.agent.controller;

import com.itlk.myclaudecode.agent.Entity.Result;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

}
