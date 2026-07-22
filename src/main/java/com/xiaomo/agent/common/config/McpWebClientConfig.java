package com.xiaomo.agent.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class McpWebClientConfig {

    @Value("${DASHSCOPE_API_KEY:}")
    private String dashscopeApiKey;

    public String getDashscopeApiKey() {
        return dashscopeApiKey;
    }
}
