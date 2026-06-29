package com.itlk.myclaudecode.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.tool")
public record ToolGuardProperties(
        int maxIterations,
        int softLimit,
        int infoGainWindow,
        double infoGainThreshold,
        int repetitionThreshold,
        int maxFetches,
        int maxConsecutiveNoNewInfo,
        int maxSearchRounds
) {
    public ToolGuardProperties {
        if (maxIterations <= 0) maxIterations = 30;
        if (softLimit <= 0) softLimit = 10;
        if (infoGainWindow <= 0) infoGainWindow = 3;
        if (infoGainThreshold <= 0) infoGainThreshold = 0.8;
        if (repetitionThreshold <= 0) repetitionThreshold = 3;
        if (maxFetches <= 0) maxFetches = 3;
        if (maxConsecutiveNoNewInfo <= 0) maxConsecutiveNoNewInfo = 2;
        if (maxSearchRounds <= 0) maxSearchRounds = 1;
    }
}
