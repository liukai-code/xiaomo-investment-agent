package com.xiaomo.agent.memory.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.conversation.entity.ChatMessage;
import com.xiaomo.agent.conversation.entity.MessageRole;
import com.xiaomo.agent.conversation.repository.ChatMessageRepository;
import com.xiaomo.agent.memory.entity.*;
import com.xiaomo.agent.memory.repository.ConversationSummaryRepository;
import com.xiaomo.agent.memory.repository.MemoryExtractionTaskRepository;
import com.xiaomo.agent.memory.repository.UserProfileRepository;
import com.xiaomo.agent.memory.service.MemoryService;
import com.xiaomo.agent.user.dto.UserPreferences;
import com.xiaomo.agent.user.service.UserPreferencesCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryExtractionService 异步记忆提取测试")
class MemoryExtractionServiceImplTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ConversationSummaryRepository summaryRepository;
    @Mock private MemoryExtractionTaskRepository extractionTaskRepository;
    @Mock private MemoryService memoryService;
    @Mock private UserPreferencesCacheService userPreferencesCacheService;
    @Mock private ChatModel chatModel;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private MemoryExtractionServiceImpl extractionService;

    private ChatMessage userMsg;
    private ChatMessage assistantMsg;

    @BeforeEach
    void setUp() {
        userMsg = new ChatMessage();
        userMsg.setId(1L);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent("我喜欢价值投资，偏好稳健型");

        assistantMsg = new ChatMessage();
        assistantMsg.setId(2L);
        assistantMsg.setRole(MessageRole.ASSISTANT);
        assistantMsg.setContent("好的，了解您的投资偏好。");

        // 默认 mock 用户偏好缓存（compressionEnabled=true）
        UserPreferences defaultPrefs = new UserPreferences(100L, "user_123456", "test@example.com", 0.7, 4096, 50, true, true);
        lenient().when(userPreferencesCacheService.getPreferences(100L)).thenReturn(defaultPrefs);
    }

    // ==================== extractMemoriesAsync ====================

    @Nested
    @DisplayName("extractMemoriesAsync 异步提取入口")
    class ExtractMemoriesAsyncTest {

        @Test
        @DisplayName("已有进行中的任务时跳过")
        void skipIfAlreadyProcessing() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(true);

            extractionService.extractMemoriesAsync(100L, 10L, null);

            verify(chatMessageRepository, never()).countByConversationId(any());
        }

        @Test
        @DisplayName("创建任务记录并检查消息数量")
        void createTaskAndCheckMessages() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(false);
            when(extractionTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            // 3 条消息，3 % 5 != 0，不触发画像提取
            when(chatMessageRepository.countByConversationId(10L)).thenReturn(3L);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);

            extractionService.extractMemoriesAsync(100L, 10L, null);

            // 验证创建了任务记录
            verify(extractionTaskRepository, atLeastOnce()).save(any(MemoryExtractionTask.class));
            // 验证检查了消息数量（压缩检查 + 频率检查各调用一次）
            verify(chatMessageRepository, times(2)).countByConversationId(10L);
            // 3 % 5 != 0，不应触发画像提取
            verify(chatMessageRepository, never()).findRecentByConversationId(anyLong(), anyInt());
        }

        @Test
        @DisplayName("任务异常时记录失败状态")
        void taskFailed() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(false);
            when(extractionTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(chatMessageRepository.countByConversationId(10L))
                    .thenThrow(new RuntimeException("DB 连接失败"));

            assertDoesNotThrow(() -> extractionService.extractMemoriesAsync(100L, 10L, null));

            verify(extractionTaskRepository, atLeastOnce()).save(argThat(task ->
                    task.getStatus() == ExtractionTaskStatus.FAILED
                            && "DB 连接失败".equals(task.getErrorMessage())
            ));
        }
    }

    // ==================== 画像提取频率控制 ====================

    @Nested
    @DisplayName("画像提取频率控制")
    class ProfileExtractionFrequencyTest {

        @Test
        @DisplayName("消息数为 5 的倍数时进入画像提取路径")
        void triggerAtMultiplesOf5() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(false);
            when(extractionTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(chatMessageRepository.countByConversationId(10L)).thenReturn(10L);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);
            lenient().when(chatMessageRepository.findRecentByConversationId(10L, 10))
                    .thenReturn(List.of(userMsg, assistantMsg));
            lenient().when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(Collections.emptyList());

            // chatModel 未 mock，AI 调用会失败但被内部 catch
            extractionService.extractMemoriesAsync(100L, 10L, null);

            // 验证进入了画像提取逻辑（查询了最近消息）
            verify(chatMessageRepository).findRecentByConversationId(10L, 10);
        }

        @Test
        @DisplayName("消息数不是 5 的倍数时不触发画像提取")
        void skipWhenNotMultiple() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(false);
            when(extractionTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(chatMessageRepository.countByConversationId(10L)).thenReturn(7L);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);

            extractionService.extractMemoriesAsync(100L, 10L, null);

            verify(chatMessageRepository, never()).findRecentByConversationId(anyLong(), anyInt());
        }
    }

    // ==================== 对话摘要压缩触发 ====================

    @Nested
    @DisplayName("对话摘要压缩触发")
    class CompressionTriggerTest {

        @Test
        @DisplayName("未压缩消息 >= 20 条时进入压缩路径")
        void triggerCompression() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(false);
            when(extractionTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(chatMessageRepository.countByConversationId(10L)).thenReturn(25L);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);
            lenient().when(chatMessageRepository.findByConversationIdOrderByIdAsc(10L))
                    .thenReturn(List.of(userMsg, assistantMsg));
            // 25 % 5 == 0，也会触发画像提取（但 AI 调用会失败，被内部 catch）
            lenient().when(chatMessageRepository.findRecentByConversationId(10L, 10))
                    .thenReturn(List.of(userMsg, assistantMsg));
            lenient().when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(Collections.emptyList());

            extractionService.extractMemoriesAsync(100L, 10L, null);

            // 验证进入了压缩逻辑（查询了全量消息）
            verify(chatMessageRepository).findByConversationIdOrderByIdAsc(10L);
        }

        @Test
        @DisplayName("已有摘要时，未压缩消息不足阈值不触发")
        void belowThresholdWithExisting() {
            when(extractionTaskRepository.existsByConversationIdAndStatus(10L, ExtractionTaskStatus.PROCESSING))
                    .thenReturn(false);
            when(extractionTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(chatMessageRepository.countByConversationId(10L)).thenReturn(30L);
            ConversationSummary existing = new ConversationSummary();
            existing.setCompressedCount(20);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(existing);
            // 30 % 5 == 0，触发画像提取（但 AI 调用会失败，被内部 catch）
            lenient().when(chatMessageRepository.findRecentByConversationId(10L, 10))
                    .thenReturn(List.of(userMsg, assistantMsg));
            lenient().when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(Collections.emptyList());

            extractionService.extractMemoriesAsync(100L, 10L, null);

            // 不应进入压缩逻辑
            verify(chatMessageRepository, never()).findByConversationIdOrderByIdAsc(10L);
        }
    }

    // ==================== saveExtractedProfiles 直接测试 ====================

    @Nested
    @DisplayName("saveExtractedProfiles 画像保存逻辑")
    class SaveExtractedProfilesTest {

        @Test
        @DisplayName("解析有效 JSON 并保存画像")
        void parseAndSave() throws Exception {
            String json = "[{\"category\":\"RISK_PREFERENCE\",\"content\":\"偏好稳健型\",\"importance\":4}]";
            List<Map<String, Object>> parsed = List.of(
                    Map.of("category", "RISK_PREFERENCE", "content", "偏好稳健型", "importance", 4)
            );
            when(objectMapper.readValue(eq(json), any(TypeReference.class))).thenReturn(parsed);
            when(userProfileRepository.existsByUserIdAndContent(100L, "偏好稳健型")).thenReturn(false);
            when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            extractionService.saveExtractedProfiles(100L, 10L, json);

            verify(userProfileRepository).save(argThat(p ->
                    p.getCategory() == ProfileCategory.RISK_PREFERENCE
                            && "偏好稳健型".equals(p.getContent())
                            && p.getImportance() == 4
                            && p.getSourceType() == MemorySourceType.AI_EXTRACTED
                            && p.getUserId().equals(100L)
            ));
        }

        @Test
        @DisplayName("与已有画像重复时不重复保存")
        void deduplication() throws Exception {
            String json = "[{\"category\":\"RISK_PREFERENCE\",\"content\":\"偏好稳健型\",\"importance\":4}]";
            List<Map<String, Object>> parsed = List.of(
                    Map.of("category", "RISK_PREFERENCE", "content", "偏好稳健型", "importance", 4)
            );
            when(objectMapper.readValue(eq(json), any(TypeReference.class))).thenReturn(parsed);
            when(userProfileRepository.existsByUserIdAndContent(100L, "偏好稳健型")).thenReturn(true);

            extractionService.saveExtractedProfiles(100L, 10L, json);

            verify(userProfileRepository, never()).save(any(UserProfile.class));
        }

        @Test
        @DisplayName("AI 返回空数组时不保存")
        void emptyArray() throws Exception {
            String json = "[]";
            when(objectMapper.readValue(eq(json), any(TypeReference.class)))
                    .thenReturn(Collections.emptyList());

            extractionService.saveExtractedProfiles(100L, 10L, json);

            verify(userProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("JSON 含 markdown 代码块时清理后解析")
        void withMarkdownBlock() throws Exception {
            String json = "```json\n[{\"category\":\"GENERAL\",\"content\":\"测试\",\"importance\":3}]\n```";
            // 清理后变成纯 JSON
            String cleaned = "[{\"category\":\"GENERAL\",\"content\":\"测试\",\"importance\":3}]";
            List<Map<String, Object>> parsed = List.of(
                    Map.of("category", "GENERAL", "content", "测试", "importance", 3)
            );
            when(objectMapper.readValue(eq(cleaned), any(TypeReference.class))).thenReturn(parsed);
            when(userProfileRepository.existsByUserIdAndContent(100L, "测试")).thenReturn(false);
            when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            extractionService.saveExtractedProfiles(100L, 10L, json);

            verify(userProfileRepository).save(any());
        }

        @Test
        @DisplayName("无效 JSON 时不抛异常")
        void invalidJson() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenThrow(new RuntimeException("parse error"));

            assertDoesNotThrow(() -> extractionService.saveExtractedProfiles(100L, 10L, "invalid"));
            verify(userProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("category 未知时回退到 GENERAL")
        void unknownCategory() throws Exception {
            String json = "[{\"category\":\"UNKNOWN\",\"content\":\"内容\",\"importance\":3}]";
            List<Map<String, Object>> parsed = List.of(
                    Map.of("category", "UNKNOWN", "content", "内容", "importance", 3)
            );
            when(objectMapper.readValue(eq(json), any(TypeReference.class))).thenReturn(parsed);
            when(userProfileRepository.existsByUserIdAndContent(100L, "内容")).thenReturn(false);
            when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            extractionService.saveExtractedProfiles(100L, 10L, json);

            verify(userProfileRepository).save(argThat(p ->
                    p.getCategory() == ProfileCategory.GENERAL));
        }
    }
}
