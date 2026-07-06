package com.itlk.myclaudecode.conversation.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface UsageRecordService {
    void record(Long userId, Long conversationId, Long inputTokens, Long outputTokens, Integer toolCallCount);
    UsageStatsDTO getStats(Long userId);

    /**
     * 估算 input tokens：从消息列表总字符数除以3.5（中英混合内容平均比率）。
     * 用于 Spring AI 流式模式无法从 Anthropic 拿到 input_tokens 时的兜底。
     */
    static long estimateInputTokens(List<Message> messages) {
        long totalChars = 0;
        for (Message msg : messages) {
            String text = msg.getText();
            if (text != null) {
                totalChars += text.length();
            }
        }
        return Math.max(1, totalChars * 10 / 35);
    }

    /**
     * 从文本内容估算 input tokens。
     */
    static long estimateInputTokensFromText(String text) {
        if (text == null || text.isEmpty()) return 1;
        return Math.max(1, text.length() * 10 / 35);
    }
}
