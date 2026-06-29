package com.itlk.myclaudecode.workflow.state;

import java.time.Instant;

public record AgentReport(String agentName, String reportContent, Instant generatedAt) {}
