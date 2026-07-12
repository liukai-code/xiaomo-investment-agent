package com.itlk.myclaudecode.notification.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.notification.entity.Notification;
import com.itlk.myclaudecode.notification.service.NotificationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    @Resource
    private NotificationService notificationService;

    @SuppressWarnings("unchecked")
    @PostMapping
    public Result<Notification> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        if (title == null || title.isBlank()) {
            return Result.error("标题不能为空");
        }
        if (content == null || content.isBlank()) {
            return Result.error("内容不能为空");
        }

        // 解析 targetUserIds（可选）
        List<Long> targetUserIds = null;
        Object targetObj = body.get("targetUserIds");
        if (targetObj instanceof List<?> targetList && !targetList.isEmpty()) {
            targetUserIds = targetList.stream()
                    .map(item -> item instanceof Number ? ((Number) item).longValue() : Long.parseLong(item.toString()))
                    .toList();
        }

        Notification notification = notificationService.create(title, content, targetUserIds);
        return Result.success(notification);
    }

    @GetMapping
    public Result<List<Notification>> list() {
        return Result.success(notificationService.listAll());
    }

    @GetMapping("/{id}/recipients")
    public Result<List<Long>> recipients(@PathVariable Long id) {
        return Result.success(notificationService.getTargetUsers(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return Result.success();
    }
}
