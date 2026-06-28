package com.itlk.myclaudecode.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class McpKeepAliveService {

    private final List<McpSyncClient> mcpClients;
    private final McpSseClientProperties sseProperties;
    private final McpClientCommonProperties commonProperties;
    private final ObjectMapper objectMapper;

    public McpKeepAliveService(List<McpSyncClient> mcpClients,
                               McpSseClientProperties sseProperties,
                               McpClientCommonProperties commonProperties,
                               ObjectMapper objectMapper) {
        this.mcpClients = mcpClients;
        this.sseProperties = sseProperties;
        this.commonProperties = commonProperties;
        this.objectMapper = objectMapper;
        log.info("MCP 保活服务已初始化, 客户端数量: {}", mcpClients.size());
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void keepAlive() {
        for (int i = 0; i < mcpClients.size(); i++) {
            McpSyncClient client = mcpClients.get(i);
            try {
                CompletableFuture.runAsync(() -> client.ping())
                        .get(10, TimeUnit.SECONDS);
                log.debug("MCP 保活 ping 成功");
            } catch (TimeoutException e) {
                log.warn("MCP 保活 ping 超时(10s), 尝试重建客户端...");
                rebuildClient(i);
            } catch (Exception e) {
                log.warn("MCP 保活 ping 异常: {}, 尝试重建客户端...", e.getMessage());
                rebuildClient(i);
            }
        }
    }

    private void rebuildClient(int index) {
        try {
            Map<String, McpSseClientProperties.SseParameters> connections = sseProperties.getConnections();
            if (connections == null || connections.isEmpty()) {
                log.error("MCP 重连失败: 无 SSE 连接配置");
                return;
            }

            Map.Entry<String, McpSseClientProperties.SseParameters> entry =
                    connections.entrySet().iterator().next();
            String url = entry.getValue().url();
            String sseEndpoint = entry.getValue().sseEndpoint();

            HttpClientSseClientTransport transport = HttpClientSseClientTransport
                    .builder(url)
                    .sseEndpoint(sseEndpoint)
                    .objectMapper(objectMapper)
                    .build();

            McpSyncClient newClient = McpClient.sync(transport)
                    .requestTimeout(commonProperties.getRequestTimeout())
                    .build();
            newClient.initialize();

            // 验证连接可用
            CompletableFuture.runAsync(() -> newClient.ping())
                    .get(10, TimeUnit.SECONDS);

            // 关闭旧客户端
            try { mcpClients.get(index).close(); } catch (Exception ignored) {}

            mcpClients.set(index, newClient);
            log.info("MCP 客户端重建成功, 服务: {}", entry.getKey());
        } catch (Exception e) {
            log.error("MCP 客户端重建失败: {}", e.getMessage());
        }
    }
}
