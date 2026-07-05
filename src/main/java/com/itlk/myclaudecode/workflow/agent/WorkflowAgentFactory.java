package com.itlk.myclaudecode.workflow.agent;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialCalcRouterTool;
import com.itlk.myclaudecode.tool.FinancialDataRouterTool;
import com.itlk.myclaudecode.tool.SqlTool;
import com.itlk.myclaudecode.tool.WebFetchTool;
import com.itlk.myclaudecode.tool.astock.*;
import com.itlk.myclaudecode.tool.config.ToolConfigService;
import com.itlk.myclaudecode.tool.config.ToolEnabledCheckWrapper;
import com.itlk.myclaudecode.workflow.node.AnalystNode;
import com.itlk.myclaudecode.workflow.node.DebateNode;
import com.itlk.myclaudecode.workflow.node.JudgeNode;
import com.itlk.myclaudecode.workflow.node.TraderNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WorkflowAgentFactory {

    private final ChatModel chatModel;
    private final ToolCallback[] allToolCallbacks;
    private final ToolConfigService toolConfigService;
    private final ToolGuardProperties guardProperties;

    public WorkflowAgentFactory(
            ChatModel chatModel,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            FileListTool fileListTool,
            FinancialCalcRouterTool financialCalcRouterTool,
            FinancialDataRouterTool financialDataRouterTool,
            SqlTool sqlTool,
            WebFetchTool webFetchTool,
            AStockQuoteRouterTool aStockQuoteRouterTool,
            AStockReportRouterTool aStockReportRouterTool,
            AStockSignalRouterTool aStockSignalRouterTool,
            AStockCapitalRouterTool aStockCapitalRouterTool,
            AStockNewsRouterTool aStockNewsRouterTool,
            AStockLimitUpRouterTool aStockLimitUpRouterTool,
            AStockOptionRouterTool aStockOptionRouterTool,
            AStockSentimentRouterTool aStockSentimentRouterTool,
            ToolCallbackProvider mcpProvider,
            ToolConfigService toolConfigService,
            ToolGuardProperties guardProperties) {

        this.chatModel = chatModel;
        this.toolConfigService = toolConfigService;
        this.guardProperties = guardProperties;

        // 复用 AgentLoopImpl 相同的工具注册模式
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(fileReadTool, fileWriteTool, fileListTool,
                        financialCalcRouterTool, financialDataRouterTool, sqlTool, webFetchTool,
                        aStockQuoteRouterTool, aStockReportRouterTool, aStockSignalRouterTool,
                        aStockCapitalRouterTool, aStockNewsRouterTool, aStockLimitUpRouterTool,
                        aStockOptionRouterTool, aStockSentimentRouterTool)
                .build();

        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallback cb : provider.getToolCallbacks()) {
            callbacks.add(new ToolEnabledCheckWrapper(cb, toolConfigService));
        }

        // 注册 MCP 工具
        if (mcpProvider != null) {
            for (ToolCallback mcp : mcpProvider.getToolCallbacks()) {
                callbacks.add(new ToolEnabledCheckWrapper(mcp, toolConfigService));
            }
        }

        this.allToolCallbacks = callbacks.toArray(new ToolCallback[0]);
        log.info("WorkflowAgentFactory 初始化完成，共 {} 个工具: {}",
                allToolCallbacks.length,
                Arrays.stream(allToolCallbacks).map(cb -> cb.getToolDefinition().name()).toList());
    }

    /**
     * 创建分析师节点（按角色过滤工具）
     */
    public AnalystNode createAnalyst(AgentRole role) {
        Set<String> allowedTools = Set.copyOf(role.toolNames());
        List<ToolCallback> scoped = Arrays.stream(allToolCallbacks)
                .filter(cb -> allowedTools.contains(cb.getToolDefinition().name()))
                .collect(Collectors.toList());
        log.info("创建分析师 {}，请求 {} 个工具，实际绑定 {} 个: {}",
                role.roleName(), allowedTools.size(), scoped.size(),
                scoped.stream().map(cb -> cb.getToolDefinition().name()).toList());

        // 检查是否有请求的工具未找到
        Set<String> registeredNames = scoped.stream()
                .map(cb -> cb.getToolDefinition().name()).collect(Collectors.toSet());
        Set<String> missing = allowedTools.stream()
                .filter(name -> !registeredNames.contains(name))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            log.warn("[{}] 以下工具未找到，可能名称不匹配: {}", role.roleName(), missing);
        }
        return new AnalystNode(chatModel, role.roleName(), role.systemPrompt(), scoped, guardProperties, role.guardConfig());
    }

    /**
     * 创建辩论节点（无工具）
     */
    public DebateNode createDebateNode(AgentRole role) {
        return new DebateNode(chatModel, role.roleName(), role.systemPrompt());
    }

    /**
     * 创建裁决节点（无工具）
     */
    public JudgeNode createJudgeNode(AgentRole role) {
        return new JudgeNode(chatModel, role.roleName(), role.systemPrompt());
    }

    /**
     * 创建交易员节点（绑定计算工具）
     */
    public TraderNode createTraderNode() {
        Set<String> allowedTools = Set.copyOf(AgentRole.TRADER.toolNames());
        List<ToolCallback> scoped = Arrays.stream(allToolCallbacks)
                .filter(cb -> allowedTools.contains(cb.getToolDefinition().name()))
                .collect(Collectors.toList());
        log.info("创建交易员，绑定 {} 个工具", scoped.size());
        return new TraderNode(chatModel, AgentRole.TRADER.roleName(),
                AgentRole.TRADER.systemPrompt(), scoped, guardProperties, AgentRole.TRADER.guardConfig());
    }
}
