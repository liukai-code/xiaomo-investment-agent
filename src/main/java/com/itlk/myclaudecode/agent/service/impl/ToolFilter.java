package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.agent.intent.IntentResult;
import com.itlk.myclaudecode.tool.config.ToolCallbackContextWrapper;
import com.itlk.myclaudecode.tool.config.ToolConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据工具开关配置、意图白名单、MCP 工具集合、标的锁定等条件过滤可用工具。
 */
@Component
@Slf4j
public class ToolFilter {

    /** 标的锁定时主动过滤的持仓工具，防止任务漂移 */
    private static final Set<String> HOLDINGS_TOOL_NAMES = Set.of(
            "getMyHoldings", "getMyAccountSummary"
    );

    @Resource
    private ToolConfigService toolConfigService;

    public List<ToolCallback> filter(List<ToolCallback> allWrappedCallbacks,
                                     Set<String> mcpToolNames,
                                     Set<String> intentToolWhitelist,
                                     IntentResult.ResolvedTarget target) {
        Set<String> enabledNames = toolConfigService.listAll().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<ToolCallback> enabledTools = allWrappedCallbacks.stream()
                .filter(cb -> {
                    String name = cb.getToolDefinition().name();
                    if (!enabledNames.contains(name)) return false;
                    if (mcpToolNames.contains(name)) return true;
                    if (intentToolWhitelist != null && !intentToolWhitelist.contains(name)) return false;
                    return true;
                })
                .<ToolCallback>map(cb -> new ToolCallbackContextWrapper(cb))
                .toList();

        // 标的锁定时，主动移除持仓工具，防止任务漂移
        if (target != null) {
            enabledTools = enabledTools.stream()
                    .filter(cb -> !HOLDINGS_TOOL_NAMES.contains(cb.getToolDefinition().name()))
                    .toList();
        }

        return enabledTools;
    }
}
