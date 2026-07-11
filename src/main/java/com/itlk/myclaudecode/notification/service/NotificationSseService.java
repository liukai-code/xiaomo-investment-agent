package com.itlk.myclaudecode.notification.service;

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

    public Flux<String> getNotificationStream() {
        return sink.asFlux();
    }
}
