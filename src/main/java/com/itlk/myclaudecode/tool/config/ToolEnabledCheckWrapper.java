package com.itlk.myclaudecode.tool.config;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class ToolEnabledCheckWrapper implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolConfigService configService;

    public ToolEnabledCheckWrapper(ToolCallback delegate, ToolConfigService configService) {
        this.delegate = delegate;
        this.configService = configService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String functionInput) {
        String toolName = getToolDefinition().name();
        if (!configService.isEnabled(toolName)) {
            return "该工具（" + toolName + "）已被管理员禁用，无法使用。请直接用自身知识回答用户问题。";
        }
        return delegate.call(functionInput);
    }
}
