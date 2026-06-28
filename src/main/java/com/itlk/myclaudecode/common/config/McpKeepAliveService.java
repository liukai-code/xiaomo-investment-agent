package com.itlk.myclaudecode.common.config;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class McpKeepAliveService {

    private final List<McpSyncClient> mcpClients;

    public McpKeepAliveService(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
        log.info("MCP 保活服务已初始化, 客户端数量: {}", mcpClients.size());
    }

    @Scheduled(fixedDelay = 180000, initialDelay = 60000)
    public void keepAlive() {
        for (McpSyncClient client : mcpClients) {
            try {
                client.ping();
                log.debug("MCP 保活 ping 成功");
            } catch (Exception e) {
                log.warn("MCP 保活 ping 失败, 将在下次请求时重连: {}", e.getMessage());
            }
        }
    }
}
