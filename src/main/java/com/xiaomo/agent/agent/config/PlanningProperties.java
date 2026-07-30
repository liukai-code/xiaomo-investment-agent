package com.xiaomo.agent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.planning")
public record PlanningProperties(
        boolean enabled,
        int maxSteps,
        int planMaxTokens,
        int scratchpadMaxLength
) {
    public PlanningProperties {
        if (maxSteps <= 0) maxSteps = 5;
        if (planMaxTokens <= 0) planMaxTokens = 1024;
        if (scratchpadMaxLength <= 0) scratchpadMaxLength = 200;
    }
}
