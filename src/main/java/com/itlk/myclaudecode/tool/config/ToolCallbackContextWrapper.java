package com.itlk.myclaudecode.tool.config;

import com.itlk.myclaudecode.tool.YangJiBaoTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

@Slf4j
public class ToolCallbackContextWrapper implements ToolCallback {

    private final ToolCallback delegate;

    public ToolCallbackContextWrapper(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 从 toolContext 取 conversationId，查 userId 设置到 ThreadLocal
        if (toolContext != null) {
            Object convId = toolContext.getContext().get("conversationId");
            if (convId != null) {
                Long userId = YangJiBaoTool.getUserId(convId.toString());
                if (userId != null) {
                    YangJiBaoTool.setCurrentUserId(userId);
                }
            }
        }
        try {
            return delegate.call(toolInput, toolContext);
        } finally {
            YangJiBaoTool.clearCurrentUserId();
        }
    }
}
