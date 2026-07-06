package com.itlk.myclaudecode.conversation.service;

import lombok.Data;

@Data
public class UsageStatsDTO {
    private Long totalRequests;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalToolCalls;
    private Long totalConversations;
    private Long totalMessages;
}
