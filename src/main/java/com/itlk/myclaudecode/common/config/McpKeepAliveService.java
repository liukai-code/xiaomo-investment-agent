package com.itlk.myclaudecode.common.config;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class McpKeepAliveService {

    private final List<McpSyncClient> mcpClients;

    public McpKeepAliveService(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
        log.info("MCP 保活服务已初始化, 客户端数量: {}", mcpClients.size());
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void keepAlive() {
        for (McpSyncClient client : mcpClients) {
            try {
                CompletableFuture.runAsync(() -> client.ping())
                        .get(10, TimeUnit.SECONDS);
                log.debug("MCP 保活 ping 成功");
            } catch (TimeoutException e) {
                log.warn("MCP 保活 ping 超时(10s)");
            } catch (Exception e) {
                log.warn("MCP 保活 ping 异常: {}", e.getMessage());
            }
        }
    }
}
