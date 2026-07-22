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

    /**
     * 查找统计重置时间点，优先激活配置，回退到任意一条配置
     */
    private LocalDateTime findStatsResetAt(Long userId) {
        Optional<UserConfig> config = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        if (config.isPresent() && config.get().getStatsResetAt() != null) {
            return config.get().getStatsResetAt();
        }
        // 回退：找任意一条配置
        var allConfigs = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
        for (UserConfig cfg : allConfigs) {
            if (cfg.getStatsResetAt() != null) {
                return cfg.getStatsResetAt();
            }
        }
        return null;
    }

    /**
     * 查找要更新的配置，优先激活配置，回退到任意一条
     */
    private UserConfig findConfigForUpdate(Long userId) {
        Optional<UserConfig> config = userConfigRepository.findByUserIdAndIsActiveTrue(userId);
        if (config.isPresent()) {
            return config.get();
        }
        var allConfigs = userConfigRepository.findByUserIdOrderByCreatedAtAsc(userId);
        return allConfigs.isEmpty() ? null : allConfigs.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageStatsDTO getStats(Long userId) {
        LocalDateTime since = findStatsResetAt(userId);

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
        UserConfig cfg = findConfigForUpdate(userId);
        if (cfg == null) {
            // 用户没有任何配置（如纯免费额度用户），创建一条用于记录重置时间
            cfg = new UserConfig();
            cfg.setUserId(userId);
            cfg.setChannelName("统计记录");
            cfg.setIsActive(false);
        }
        cfg.setStatsResetAt(LocalDateTime.now());
        userConfigRepository.save(cfg);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyUsageDTO> getDailyStats(Long userId) {
        LocalDateTime since = findStatsResetAt(userId);

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
