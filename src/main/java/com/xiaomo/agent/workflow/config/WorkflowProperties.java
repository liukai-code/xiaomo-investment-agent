package com.xiaomo.agent.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow.deep-analysis")
public record WorkflowProperties(
        boolean enabled,
        int bullBearRounds,
        int riskRounds,
        int analystMaxTokens,
        double analystTemperature,
        int debateMaxTokens,
        double debateTemperature,
        int timeoutSeconds,
        int stageMinSeconds
) {
    public WorkflowProperties {
        // 默认值
        if (bullBearRounds == 0) bullBearRounds = 2;
        if (riskRounds == 0) riskRounds = 2;
        if (analystMaxTokens == 0) analystMaxTokens = 8192;
        if (analystTemperature == 0) analystTemperature = 0.4;
        if (debateMaxTokens == 0) debateMaxTokens = 4096;
        if (debateTemperature == 0) debateTemperature = 0.5;
        if (timeoutSeconds == 0) timeoutSeconds = 600;
        if (stageMinSeconds == 0) stageMinSeconds = 15;
    }
}
