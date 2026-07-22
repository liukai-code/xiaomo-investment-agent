package com.xiaomo.agent.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

@Slf4j
@Service
public class NotificationSseService implements MessageListener {

    private static final String REDIS_CHANNEL = "notification:broadcast";

    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    @Resource
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Resource
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(
                new MessageListenerAdapter(this, "onMessage"),
                new ChannelTopic(REDIS_CHANNEL));
        log.info("通知 SSE 服务已启动，订阅 Redis Channel: {}", REDIS_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.debug("收到 Redis 通知广播: {}", body);
        sink.tryEmitNext(body);
    }

    /**
     * 获取按用户过滤的通知流
     * 广播通知推送给所有人，定向通知只推送给目标用户
     */
    public Flux<String> getNotificationStream(Long userId) {
        return sink.asFlux().filter(body -> {
            try {
                JsonNode node = objectMapper.readTree(body);
                boolean broadcast = node.has("broadcast") && node.get("broadcast").asBoolean(true);
                if (broadcast) return true;

                // 定向通知：检查 userId 是否在 targetUserIds 中
                JsonNode targetNode = node.get("targetUserIds");
                if (targetNode == null || !targetNode.isArray()) return false;
                for (JsonNode id : targetNode) {
                    if (id.asLong() == userId) return true;
                }
                return false;
            } catch (Exception e) {
                log.warn("解析通知消息失败: {}", e.getMessage());
                return false;
            }
        });
    }
}
