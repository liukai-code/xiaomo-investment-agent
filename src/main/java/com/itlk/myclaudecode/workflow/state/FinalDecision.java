package com.itlk.myclaudecode.workflow.state;

import java.time.Instant;

public record FinalDecision(
        String action,       // "BUY", "SELL", "HOLD"
        double confidence,   // 0.0 ~ 1.0
        double targetPrice,
        String summary,
        Instant decidedAt
) {}
