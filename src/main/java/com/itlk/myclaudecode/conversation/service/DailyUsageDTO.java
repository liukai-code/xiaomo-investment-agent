package com.itlk.myclaudecode.conversation.service;

import lombok.Data;

@Data
public class DailyUsageDTO {
    private String date;
    private Long inputTokens;
    private Long outputTokens;
    private Long toolCalls;
    private Long requestCount;
}
