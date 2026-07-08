package com.itlk.myclaudecode.conversation.service;

import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.repository.ConversationRepository;
import com.itlk.myclaudecode.conversation.repository.UsageRecordRepository;
import com.itlk.myclaudecode.conversation.service.impl.UsageRecordServiceImpl;
import com.itlk.myclaudecode.user.config.UserConfig;
import com.itlk.myclaudecode.user.config.UserConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageRecordService 用量统计测试")
class UsageRecordServiceTest {

    @Mock private UsageRecordRepository usageRecordRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private UserConfigRepository userConfigRepository;

    @InjectMocks
    private UsageRecordServiceImpl usageRecordService;

    private UserConfig configWithReset;
    private UserConfig configWithoutReset;

    @BeforeEach
    void setUp() {
        configWithReset = new UserConfig();
        configWithReset.setId(1L);
        configWithReset.setUserId(100L);
        configWithReset.setIsActive(true);
        configWithReset.setStatsResetAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        configWithoutReset = new UserConfig();
        configWithoutReset.setId(2L);
        configWithoutReset.setUserId(200L);
        configWithoutReset.setIsActive(true);
        configWithoutReset.setStatsResetAt(null);
    }

    // ==================== getStats ====================

    @Nested
    @DisplayName("getStats 获取统计")
    class GetStatsTest {

        @Test
        @DisplayName("无重置记录时统计全量数据")
        void statsWithoutReset() {
            when(userConfigRepository.findByUserIdAndIsActiveTrue(200L))
                    .thenReturn(Optional.of(configWithoutReset));
            when(usageRecordRepository.countByUserId(200L)).thenReturn(5L);
            when(usageRecordRepository.sumInputTokensByUserId(200L)).thenReturn(1000L);
            when(usageRecordRepository.sumOutputTokensByUserId(200L)).thenReturn(2000L);
            when(usageRecordRepository.sumToolCallCountByUserId(200L)).thenReturn(10L);
            when(conversationRepository.countByUserId(200L)).thenReturn(3L);
            when(chatMessageRepository.countByUserId(200L)).thenReturn(15L);

            UsageStatsDTO stats = usageRecordService.getStats(200L);

            assertEquals(5L, stats.getTotalRequests());
            assertEquals(1000L, stats.getTotalInputTokens());
            assertEquals(2000L, stats.getTotalOutputTokens());
            assertEquals(10L, stats.getTotalToolCalls());
            assertEquals(3L, stats.getTotalConversations());
            assertEquals(15L, stats.getTotalMessages());
        }

        @Test
        @DisplayName("有重置记录时只统计重置后的数据")
        void statsWithReset() {
            LocalDateTime since = configWithReset.getStatsResetAt();
            when(userConfigRepository.findByUserIdAndIsActiveTrue(100L))
                    .thenReturn(Optional.of(configWithReset));
            when(usageRecordRepository.countByUserIdSince(100L, since)).thenReturn(2L);
            when(usageRecordRepository.sumInputTokensByUserIdSince(100L, since)).thenReturn(500L);
            when(usageRecordRepository.sumOutputTokensByUserIdSince(100L, since)).thenReturn(800L);
            when(usageRecordRepository.sumToolCallCountByUserIdSince(100L, since)).thenReturn(3L);
            when(conversationRepository.countByUserIdSince(100L, since)).thenReturn(1L);
            when(chatMessageRepository.countByUserIdSince(100L, since)).thenReturn(4L);

            UsageStatsDTO stats = usageRecordService.getStats(100L);

            assertEquals(2L, stats.getTotalRequests());
            assertEquals(500L, stats.getTotalInputTokens());
            assertEquals(800L, stats.getTotalOutputTokens());
            assertEquals(3L, stats.getTotalToolCalls());
            assertEquals(1L, stats.getTotalConversations());
            assertEquals(4L, stats.getTotalMessages());

            // 确保没调用全量查询
            verify(usageRecordRepository, never()).countByUserId(100L);
            verify(usageRecordRepository, never()).sumInputTokensByUserId(100L);
        }
    }

    // ==================== resetStats ====================

    @Nested
    @DisplayName("resetStats 重置统计")
    class ResetStatsTest {

        @Test
        @DisplayName("重置统计只更新时间戳不删除数据")
        void resetOnlyUpdatesTimestamp() {
            when(userConfigRepository.findByUserIdAndIsActiveTrue(100L))
                    .thenReturn(Optional.of(configWithReset));
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            usageRecordService.resetStats(100L);

            verify(userConfigRepository).save(argThat(c -> c.getStatsResetAt() != null));
            verify(usageRecordRepository, never()).deleteByUserId(anyLong());
        }

        @Test
        @DisplayName("无激活渠道时回退到第一条配置")
        void resetFallsBackToFirstConfig() {
            when(userConfigRepository.findByUserIdAndIsActiveTrue(300L)).thenReturn(Optional.empty());
            UserConfig fallback = new UserConfig();
            fallback.setId(5L);
            fallback.setUserId(300L);
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(300L)).thenReturn(List.of(fallback));
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            usageRecordService.resetStats(300L);

            verify(userConfigRepository).save(argThat(c ->
                    c.getId().equals(5L) && c.getStatsResetAt() != null));
        }

        @Test
        @DisplayName("无任何配置时不报错")
        void resetWithNoConfigDoesNotThrow() {
            when(userConfigRepository.findByUserIdAndIsActiveTrue(400L)).thenReturn(Optional.empty());
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(400L)).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> usageRecordService.resetStats(400L));
            verify(userConfigRepository, never()).save(any());
        }
    }
}
