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

    @PostMapping
    public Result<Notification> create(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        if (title == null || title.isBlank()) {
            return Result.error("标题不能为空");
        }
        if (content == null || content.isBlank()) {
            return Result.error("内容不能为空");
        }
        Notification notification = notificationService.create(title, content);
        return Result.success(notification);
    }

    @GetMapping
    public Result<List<Notification>> list() {
        return Result.success(notificationService.listAll());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return Result.success();
    }
}
