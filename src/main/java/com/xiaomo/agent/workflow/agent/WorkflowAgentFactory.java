package com.xiaomo.agent.workflow.agent;

import com.xiaomo.agent.agent.config.ToolGuardProperties;
import com.xiaomo.agent.tool.FileListTool;
import com.xiaomo.agent.tool.FileReadTool;
import com.xiaomo.agent.tool.FileWriteTool;
import com.xiaomo.agent.tool.FinancialCalcRouterTool;
import com.xiaomo.agent.tool.FinancialDataRouterTool;
import com.xiaomo.agent.tool.SqlTool;
import com.xiaomo.agent.tool.WebFetchTool;
import com.xiaomo.agent.tool.astock.*;
import com.xiaomo.agent.tool.config.ToolConfigService;
import com.xiaomo.agent.tool.config.ToolEnabledCheckWrapper;
import com.xiaomo.agent.conversation.service.UsageRecordService;
import com.xiaomo.agent.workflow.node.AnalystNode;
import com.xiaomo.agent.workflow.node.DebateNode;
import com.xiaomo.agent.workflow.node.JudgeNode;
import com.xiaomo.agent.workflow.node.TraderNode;
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
    private final UsageRecordService usageRecordService;

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
            ToolGuardProperties guardProperties,
            UsageRecordService usageRecordService) {

        this.chatModel = chatModel;
        this.toolConfigService = toolConfigService;
        this.guardProperties = guardProperties;
        this.usageRecordService = usageRecordService;

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

        // 根据 Redis 配置过滤，只显示已启用的工具
        Set<String> enabledNames = toolConfigService.listAll().entrySet().stream()
                .filter(java.util.Map.Entry::getValue)
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<String> enabledToolNames = Arrays.stream(allToolCallbacks)
                .map(cb -> cb.getToolDefinition().name())
                .filter(enabledNames::contains)
                .toList();
        List<String> disabledToolNames = Arrays.stream(allToolCallbacks)
                .map(cb -> cb.getToolDefinition().name())
                .filter(name -> !enabledNames.contains(name))
                .toList();
        log.info("WorkflowAgentFactory 初始化完成，共 {} 个工具（已启用 {} 个，已禁用 {} 个）: {}",
                allToolCallbacks.length, enabledToolNames.size(), disabledToolNames.size(),
                enabledToolNames);
        if (!disabledToolNames.isEmpty()) {
            log.info("已禁用的工具: {}", disabledToolNames);
        }
    }

    /**
     * 创建分析师节点（按角色过滤工具，使用默认 ChatModel）
     */
    public AnalystNode createAnalyst(AgentRole role) {
        return createAnalyst(role, null);
    }

    /**
     * 创建分析师节点（按角色过滤工具，支持 Per-User ChatModel）
     */
    public AnalystNode createAnalyst(AgentRole role, ChatModel userChatModel) {
        ChatModel model = userChatModel != null ? userChatModel : chatModel;
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
        return new AnalystNode(model, role.roleName(), role.systemPrompt(), scoped, guardProperties, role.guardConfig(), usageRecordService);
    }

    /**
     * 创建辩论节点（无工具，使用默认 ChatModel）
     */
    public DebateNode createDebateNode(AgentRole role) {
        return createDebateNode(role, null);
    }

    /**
     * 创建辩论节点（无工具，支持 Per-User ChatModel）
     */
    public DebateNode createDebateNode(AgentRole role, ChatModel userChatModel) {
        ChatModel model = userChatModel != null ? userChatModel : chatModel;
        return new DebateNode(model, role.roleName(), role.systemPrompt(), usageRecordService);
    }

    /**
     * 创建裁决节点（无工具，使用默认 ChatModel）
     */
    public JudgeNode createJudgeNode(AgentRole role) {
        return createJudgeNode(role, null);
    }

    /**
     * 创建裁决节点（无工具，支持 Per-User ChatModel）
     */
    public JudgeNode createJudgeNode(AgentRole role, ChatModel userChatModel) {
        ChatModel model = userChatModel != null ? userChatModel : chatModel;
        return new JudgeNode(model, role.roleName(), role.systemPrompt(), usageRecordService);
    }

    /**
     * 创建交易员节点（绑定计算工具，使用默认 ChatModel）
     */
    public TraderNode createTraderNode() {
        return createTraderNode(null);
    }

    /**
     * 创建交易员节点（绑定计算工具，支持 Per-User ChatModel）
     */
    public TraderNode createTraderNode(ChatModel userChatModel) {
        ChatModel model = userChatModel != null ? userChatModel : chatModel;
        Set<String> allowedTools = Set.copyOf(AgentRole.TRADER.toolNames());
        List<ToolCallback> scoped = Arrays.stream(allToolCallbacks)
                .filter(cb -> allowedTools.contains(cb.getToolDefinition().name()))
                .collect(Collectors.toList());
        log.info("创建交易员，绑定 {} 个工具", scoped.size());
        return new TraderNode(model, AgentRole.TRADER.roleName(),
                AgentRole.TRADER.systemPrompt(), scoped, guardProperties, AgentRole.TRADER.guardConfig(), usageRecordService);
    }
}
