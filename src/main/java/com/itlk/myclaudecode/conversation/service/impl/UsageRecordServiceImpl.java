package com.itlk.myclaudecode.conversation.service.impl;

import com.itlk.myclaudecode.conversation.entity.UsageRecord;
import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.repository.ConversationRepository;
import com.itlk.myclaudecode.conversation.repository.UsageRecordRepository;
import com.itlk.myclaudecode.conversation.service.UsageRecordService;
import com.itlk.myclaudecode.conversation.service.UsageStatsDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageRecordServiceImpl implements UsageRecordService {

    @Resource
    private UsageRecordRepository usageRecordRepository;

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Override
    @Transactional
    public void record(Long userId, Long conversationId, Long inputTokens,
                       Long outputTokens, Integer toolCallCount) {
        UsageRecord record = new UsageRecord();
        record.setUserId(userId);
        record.setConversationId(conversationId);
        record.setInputTokens(inputTokens != null ? inputTokens : 0L);
        record.setOutputTokens(outputTokens != null ? outputTokens : 0L);
        record.setToolCallCount(toolCallCount != null ? toolCallCount : 0);
        usageRecordRepository.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageStatsDTO getStats(Long userId) {
        UsageStatsDTO dto = new UsageStatsDTO();
        dto.setTotalRequests(usageRecordRepository.countByUserId(userId));
        dto.setTotalInputTokens(usageRecordRepository.sumInputTokensByUserId(userId));
        dto.setTotalOutputTokens(usageRecordRepository.sumOutputTokensByUserId(userId));
        dto.setTotalToolCalls(usageRecordRepository.sumToolCallCountByUserId(userId));
        dto.setTotalConversations(conversationRepository.countByUserId(userId));
        dto.setTotalMessages(chatMessageRepository.countByUserId(userId));
        return dto;
    }
}
