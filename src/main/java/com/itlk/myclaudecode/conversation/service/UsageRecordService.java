package com.itlk.myclaudecode.conversation.service;

public interface UsageRecordService {
    void record(Long userId, Long conversationId, Long inputTokens, Long outputTokens, Integer toolCallCount);
    UsageStatsDTO getStats(Long userId);
}
