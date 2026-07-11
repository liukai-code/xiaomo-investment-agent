package com.itlk.myclaudecode.notification.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.notification.entity.Notification;
import com.itlk.myclaudecode.notification.service.NotificationService;
import com.itlk.myclaudecode.notification.service.NotificationSseService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationSseService notificationSseService;

    @GetMapping
    public Result<List<Notification>> list() {
        return Result.success(notificationService.listRecent(50));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        long count = notificationService.getUnreadCount(userId);
        return Result.success(Map.of("count", count));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        notificationService.markAsRead(userId, id);
        return Result.success();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return notificationSseService.getNotificationStream();
    }
}
