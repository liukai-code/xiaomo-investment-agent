package com.xiaomo.agent.memory.service;

public interface MemoryExtractionService {

    /**
     * 异步触发记忆提取（AI 回复后调用）
     * 包含：画像提取（每 N 轮）+ 对话摘要压缩（消息超阈值时）
     */
    void extractMemoriesAsync(Long userId, Long conversationId, Long triggerMessageId);
}
