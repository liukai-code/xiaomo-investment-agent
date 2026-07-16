package com.itlk.myclaudecode.conversation.service.impl;

import com.itlk.myclaudecode.conversation.entity.UsageRecord;
import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.repository.ConversationRepository;
import com.itlk.myclaudecode.conversation.repository.UsageRecordRepository;
import com.itlk.myclaudecode.conversation.service.DailyUsageDTO;
import com.itlk.myclaudecode.conversation.service.UsageRecordService;
import com.itlk.myclaudecode.conversation.service.UsageStatsDTO;
import com.itlk.myclaudecode.user.config.UserConfig;
import com.itlk.myclaudecode.user.config.UserConfigRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UsageRecordServiceImpl implements UsageRecordService {

    @Resource
    private UsageRecordRepository usageRecordRepository;

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private UserConfigRepository userConfigRepository;

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
        // 获取统计重置时间点
        Optional<UserConfig> config = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        LocalDateTime since = config.map(UserConfig::getStatsResetAt).orElse(null);

        UsageStatsDTO dto = new UsageStatsDTO();
        if (since != null) {
            dto.setTotalRequests(usageRecordRepository.countByUserIdSince(userId, since));
            dto.setTotalInputTokens(usageRecordRepository.sumInputTokensByUserIdSince(userId, since));
            dto.setTotalOutputTokens(usageRecordRepository.sumOutputTokensByUserIdSince(userId, since));
            dto.setTotalToolCalls(usageRecordRepository.sumToolCallCountByUserIdSince(userId, since));
            dto.setTotalConversations(conversationRepository.countByUserIdSince(userId, since));
            dto.setTotalMessages(chatMessageRepository.countByUserIdSince(userId, since));
        } else {
            dto.setTotalRequests(usageRecordRepository.countByUserId(userId));
            dto.setTotalInputTokens(usageRecordRepository.sumInputTokensByUserId(userId));
            dto.setTotalOutputTokens(usageRecordRepository.sumOutputTokensByUserId(userId));
            dto.setTotalToolCalls(usageRecordRepository.sumToolCallCountByUserId(userId));
            dto.setTotalConversations(conversationRepository.countByUserId(userId));
            dto.setTotalMessages(chatMessageRepository.countByUserId(userId));
        }
        return dto;
    }

    @Override
    @Transactional
    public void resetStats(Long userId) {
        // 只更新重置时间戳，不删除任何数据
        Optional<UserConfig> config = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        if (config.isPresent()) {
            UserConfig cfg = config.get();
            cfg.setStatsResetAt(LocalDateTime.now());
            userConfigRepository.save(cfg);
        } else {
            // 如果没有激活渠道，找任意一条配置记录
            var allConfigs = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (!allConfigs.isEmpty()) {
                UserConfig cfg = allConfigs.get(0);
                cfg.setStatsResetAt(LocalDateTime.now());
                userConfigRepository.save(cfg);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyUsageDTO> getDailyStats(Long userId) {
        Optional<UserConfig> config = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        LocalDateTime since = config.map(UserConfig::getStatsResetAt).orElse(null);

        List<Object[]> rows;
        if (since != null) {
            rows = usageRecordRepository.dailyStatsByUserIdSince(userId, since);
        } else {
            rows = usageRecordRepository.dailyStatsByUserId(userId);
        }

        List<DailyUsageDTO> result = new ArrayList<>();
        for (Object[] row : rows) {
            DailyUsageDTO dto = new DailyUsageDTO();
            dto.setDate(((LocalDate) row[0]).toString());
            dto.setInputTokens(((Number) row[1]).longValue());
            dto.setOutputTokens(((Number) row[2]).longValue());
            dto.setToolCalls(((Number) row[3]).longValue());
            dto.setRequestCount(((Number) row[4]).longValue());
            result.add(dto);
        }
        return result;
    }
}
