package com.itlk.myclaudecode.conversation.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.conversation.service.UsageRecordService;
import com.itlk.myclaudecode.conversation.service.UsageStatsDTO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage")
public class UsageStatsController {

    @Resource
    private UsageRecordService usageRecordService;

    @GetMapping("/stats")
    public Result<UsageStatsDTO> getStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        UsageStatsDTO stats = usageRecordService.getStats(userId);
        return Result.success(stats);
    }

    @DeleteMapping("/stats")
    public Result<Void> resetStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        usageRecordService.resetStats(userId);
        return Result.success();
    }
}
