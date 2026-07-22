package com.xiaomo.agent.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow.risk-override")
public record RiskOverrideProperties(
        boolean enabled,
        double vetoBuyThreshold,
        boolean downgradeOne,
        boolean downgradeTwo
) {
    public RiskOverrideProperties {
        if (vetoBuyThreshold <= 0) vetoBuyThreshold = 0.4;
    }
}
