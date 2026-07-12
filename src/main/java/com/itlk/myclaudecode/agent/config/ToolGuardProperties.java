package com.itlk.myclaudecode.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.tool")
public record ToolGuardProperties(
        int maxIterations,
        int softLimit,
        int escalationWarning,
        int escalationFinal,
        int infoGainWindow,
        double infoGainThreshold,
        int repetitionThreshold,
        int maxFetches,
        int maxConsecutiveNoNewInfo,
        int maxSearchRounds,
        int toolTimeoutSeconds,
        int maxSameToolCalls,
        int reportMinLength,
        int reportMinSections
) {
    public ToolGuardProperties {
        if (maxIterations <= 0) maxIterations = 30;
        if (softLimit <= 0) softLimit = 10;
        if (escalationWarning <= 0) escalationWarning = 15;
        if (escalationFinal <= 0) escalationFinal = 20;
        if (infoGainWindow <= 0) infoGainWindow = 3;
        if (infoGainThreshold <= 0) infoGainThreshold = 0.8;
        if (repetitionThreshold <= 0) repetitionThreshold = 3;
        if (maxFetches <= 0) maxFetches = 5;
        if (maxConsecutiveNoNewInfo <= 0) maxConsecutiveNoNewInfo = 2;
        if (maxSearchRounds <= 0) maxSearchRounds = 3;
        if (toolTimeoutSeconds <= 0) toolTimeoutSeconds = 60;
        if (maxSameToolCalls <= 0) maxSameToolCalls = 10;
        if (reportMinLength <= 0) reportMinLength = 500;
        if (reportMinSections <= 0) reportMinSections = 2;
    }
}
