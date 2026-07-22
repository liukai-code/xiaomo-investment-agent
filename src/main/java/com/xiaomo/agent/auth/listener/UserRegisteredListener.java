package com.xiaomo.agent.auth.listener;

import com.xiaomo.agent.auth.event.UserRegisteredEvent;
import com.xiaomo.agent.notification.service.NotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UserRegisteredListener {

    @Resource
    private NotificationService notificationService;

    @Async
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            notificationService.create(
                    "欢迎加入小墨！",
                    "您已获得 100,000 免费体验 Token，可以直接开始对话。额度用完后，请在设置中配置自己的 API Key 继续使用。",
                    List.of(event.getUserId())
            );
            log.info("注册欢迎通知已发送, userId={}", event.getUserId());
        } catch (Exception e) {
            log.warn("发送注册欢迎通知失败, userId={}: {}", event.getUserId(), e.getMessage());
        }
    }
}
