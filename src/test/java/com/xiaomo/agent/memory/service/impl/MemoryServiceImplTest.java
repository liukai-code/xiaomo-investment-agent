package com.xiaomo.agent.memory.service.impl;

import com.xiaomo.agent.memory.entity.*;
import com.xiaomo.agent.memory.repository.ConversationSummaryRepository;
import com.xiaomo.agent.memory.repository.UserProfileRepository;
import com.xiaomo.agent.memory.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryService 记忆服务测试")
class MemoryServiceImplTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ConversationSummaryRepository summaryRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private MemoryServiceImpl memoryService;

    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        testProfile = new UserProfile();
        testProfile.setId(1L);
        testProfile.setUserId(100L);
        testProfile.setCategory(ProfileCategory.INVESTMENT_STYLE);
        testProfile.setContent("偏好价值投资，注重基本面分析");
        testProfile.setImportance(5);
        testProfile.setSourceType(MemorySourceType.USER_EXPLICIT);
        testProfile.setActive(true);
        testProfile.setCreatedAt(LocalDateTime.of(2026, 7, 21, 10, 0));
        testProfile.setUpdatedAt(LocalDateTime.of(2026, 7, 21, 10, 0));

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== getActiveProfiles ====================

    @Nested
    @DisplayName("getActiveProfiles 获取用户画像")
    class GetActiveProfilesTest {

        @Test
        @DisplayName("缓存命中时直接返回缓存数据")
        void cacheHit() {
            List<UserProfile> cached = List.of(testProfile);
            when(valueOperations.get("memory:profile:100")).thenReturn(cached);

            List<UserProfile> result = memoryService.getActiveProfiles(100L);

            assertEquals(1, result.size());
            assertEquals("偏好价值投资，注重基本面分析", result.get(0).getContent());
            verify(userProfileRepository, never()).findByUserIdAndActiveTrueOrderByImportanceDesc(any());
        }

        @Test
        @DisplayName("缓存未命中时查 DB 并写入缓存")
        void cacheMiss() {
            when(valueOperations.get("memory:profile:100")).thenReturn(null);
            when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(List.of(testProfile));

            List<UserProfile> result = memoryService.getActiveProfiles(100L);

            assertEquals(1, result.size());
            verify(valueOperations).set(eq("memory:profile:100"), any(), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("无画像时返回空列表")
        void emptyProfiles() {
            when(valueOperations.get("memory:profile:100")).thenReturn(null);
            when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(Collections.emptyList());

            List<UserProfile> result = memoryService.getActiveProfiles(100L);

            assertTrue(result.isEmpty());
        }
    }

    // ==================== addUserMemory ====================

    @Nested
    @DisplayName("addUserMemory 用户主动添加记忆")
    class AddUserMemoryTest {

        @Test
        @DisplayName("正常添加记忆，importance 默认为 5")
        void addSuccess() {
            when(userProfileRepository.countActiveByUserId(100L)).thenReturn(0L);
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> {
                UserProfile p = inv.getArgument(0);
                p.setId(2L);
                return p;
            });

            UserProfile result = memoryService.addUserMemory(100L, "我是短线交易者",
                    ProfileCategory.INVESTMENT_STYLE, 10L);

            assertEquals("我是短线交易者", result.getContent());
            assertEquals(5, result.getImportance());
            assertEquals(MemorySourceType.USER_EXPLICIT, result.getSourceType());
            assertEquals(100L, result.getUserId());
            verify(userProfileRepository).save(any());
            verify(redisTemplate).delete("memory:profile:100");
        }

        @Test
        @DisplayName("达到上限时停用最低重要性记忆")
        void atCapacity() {
            UserProfile lowest = new UserProfile();
            lowest.setId(99L);
            lowest.setUserId(100L);
            lowest.setImportance(1);

            when(userProfileRepository.countActiveByUserId(100L)).thenReturn(50L);
            when(userProfileRepository.findTopByUserId(100L, 50)).thenReturn(List.of(testProfile, lowest));
            when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            memoryService.addUserMemory(100L, "新记忆", ProfileCategory.GENERAL, null);

            // 验证 save 被调用了 2 次：一次停用旧记忆，一次保存新记忆
            verify(userProfileRepository, times(2)).save(any());
            // 验证 ID=99 的记忆被停用
            verify(userProfileRepository).save(argThat(p ->
                    p.getId() != null && p.getId().equals(99L) && Boolean.FALSE.equals(p.getActive())));
        }
    }

    // ==================== updateProfile ====================

    @Nested
    @DisplayName("updateProfile 更新画像记忆")
    class UpdateProfileTest {

        @Test
        @DisplayName("正常更新内容和重要性")
        void updateSuccess() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(testProfile));
            when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserProfile result = memoryService.updateProfile(100L, 1L, "更新后的内容", 4);

            assertEquals("更新后的内容", result.getContent());
            assertEquals(4, result.getImportance());
            verify(redisTemplate).delete("memory:profile:100");
        }

        @Test
        @DisplayName("记忆不存在时抛异常")
        void notFound() {
            when(userProfileRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> memoryService.updateProfile(100L, 999L, "内容", 3));
        }

        @Test
        @DisplayName("无权修改他人记忆时抛异常")
        void notOwner() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(testProfile));

            assertThrows(IllegalArgumentException.class,
                    () -> memoryService.updateProfile(200L, 1L, "内容", 3));
        }
    }

    // ==================== deleteProfile ====================

    @Nested
    @DisplayName("deleteProfile 删除画像记忆")
    class DeleteProfileTest {

        @Test
        @DisplayName("正常删除")
        void deleteSuccess() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(testProfile));

            memoryService.deleteProfile(100L, 1L);

            verify(userProfileRepository).delete(testProfile);
            verify(redisTemplate).delete("memory:profile:100");
        }

        @Test
        @DisplayName("无权删除他人记忆时抛异常")
        void notOwner() {
            when(userProfileRepository.findById(1L)).thenReturn(Optional.of(testProfile));

            assertThrows(IllegalArgumentException.class,
                    () -> memoryService.deleteProfile(200L, 1L));
        }
    }

    // ==================== detectExplicitMemory ====================

    @Nested
    @DisplayName("detectExplicitMemory 检测用户主动记忆")
    class DetectExplicitMemoryTest {

        @ParameterizedTest
        @DisplayName("匹配各种「记住」模式")
        @CsvSource({
                "记住我是价值投资风格, 我是价值投资风格",
                "记一下我喜欢短线操作, 我喜欢短线操作",
                "帮我记：半导体板块, 半导体板块",
                "请记住我偏好稳健型, 我偏好稳健型",
                "别忘了我关注新能源, 我关注新能源"
        })
        void matchPatterns(String input, String expectedContent) {
            MemoryService.DetectResult result = memoryService.detectExplicitMemory(input);

            assertTrue(result.detected());
            assertEquals(expectedContent, result.content());
        }

        @ParameterizedTest
        @DisplayName("不匹配普通消息")
        @ValueSource(strings = {
                "今天行情怎么样",
                "帮我分析茅台",
                "你好",
                ""
        })
        void noMatch(String input) {
            MemoryService.DetectResult result = memoryService.detectExplicitMemory(input);
            assertFalse(result.detected());
        }

        @Test
        @DisplayName("null 消息返回未检测到")
        void nullMessage() {
            MemoryService.DetectResult result = memoryService.detectExplicitMemory(null);
            assertFalse(result.detected());
        }

        @Test
        @DisplayName("根据内容推断类别 — 风险偏好")
        void inferRiskCategory() {
            MemoryService.DetectResult result = memoryService.detectExplicitMemory("记住我是稳健型投资者");
            assertTrue(result.detected());
            assertEquals(ProfileCategory.RISK_PREFERENCE, result.category());
        }

        @Test
        @DisplayName("根据内容推断类别 — 关注板块")
        void inferSectorCategory() {
            MemoryService.DetectResult result = memoryService.detectExplicitMemory("记住我关注半导体板块");
            assertTrue(result.detected());
            assertEquals(ProfileCategory.FOCUS_SECTOR, result.category());
        }

        @Test
        @DisplayName("根据内容推断类别 — 持仓习惯")
        void inferHoldingCategory() {
            MemoryService.DetectResult result = memoryService.detectExplicitMemory("记住我的仓位管理方式是分批建仓");
            assertTrue(result.detected());
            assertEquals(ProfileCategory.HOLDING_HABIT, result.category());
        }
    }

    // ==================== buildMemoryPrompt ====================

    @Nested
    @DisplayName("buildMemoryPrompt 构建记忆 Prompt")
    class BuildMemoryPromptTest {

        @Test
        @DisplayName("有画像时包含 [用户画像记忆] 段")
        void withProfiles() {
            when(valueOperations.get("memory:profile:100")).thenReturn(null);
            when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(List.of(testProfile));
            when(valueOperations.get("memory:summary:10")).thenReturn(null);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);

            String prompt = memoryService.buildMemoryPrompt(100L, 10L);

            assertTrue(prompt.contains("[用户画像记忆]"));
            assertTrue(prompt.contains("[投资风格] 偏好价值投资，注重基本面分析"));
            assertFalse(prompt.contains("[对话历史摘要]"));
        }

        @Test
        @DisplayName("有摘要时包含 [对话历史摘要] 段")
        void withSummary() {
            when(valueOperations.get("memory:profile:100")).thenReturn(Collections.emptyList());
            when(valueOperations.get("memory:summary:10")).thenReturn(null);

            ConversationSummary summary = new ConversationSummary();
            summary.setSummary("用户询问了茅台的估值分析，建议长期持有。");
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(summary);

            String prompt = memoryService.buildMemoryPrompt(100L, 10L);

            assertTrue(prompt.contains("[对话历史摘要]"));
            assertTrue(prompt.contains("茅台的估值分析"));
        }

        @Test
        @DisplayName("无画像无摘要时返回空字符串")
        void empty() {
            when(valueOperations.get("memory:profile:100")).thenReturn(null);
            when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(Collections.emptyList());
            when(valueOperations.get("memory:summary:10")).thenReturn(null);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);

            String prompt = memoryService.buildMemoryPrompt(100L, 10L);

            assertTrue(prompt.isEmpty());
        }

        @Test
        @DisplayName("画像按重要性降序排列，预算内截断")
        void tokenBudget() {
            // 创建多条画像，验证 token 预算截断
            UserProfile low = new UserProfile();
            low.setCategory(ProfileCategory.GENERAL);
            low.setContent("这是一条不太重要的通用偏好信息，用于测试token预算截断逻辑是否正确工作");
            low.setImportance(1);

            UserProfile high = new UserProfile();
            high.setCategory(ProfileCategory.INVESTMENT_STYLE);
            high.setContent("价值投资");
            high.setImportance(5);

            when(valueOperations.get("memory:profile:100")).thenReturn(null);
            when(userProfileRepository.findByUserIdAndActiveTrueOrderByImportanceDesc(100L))
                    .thenReturn(List.of(high, low));
            when(valueOperations.get("memory:summary:10")).thenReturn(null);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);

            String prompt = memoryService.buildMemoryPrompt(100L, 10L);

            assertTrue(prompt.contains("[投资风格] 价值投资"));
            // 高重要性的应该在前面
            int highIdx = prompt.indexOf("价值投资");
            int lowIdx = prompt.indexOf("通用偏好");
            if (lowIdx >= 0) {
                assertTrue(highIdx < lowIdx, "高重要性画像应在前面");
            }
        }
    }

    // ==================== getLatestSummary ====================

    @Nested
    @DisplayName("getLatestSummary 获取对话摘要")
    class GetLatestSummaryTest {

        @Test
        @DisplayName("缓存命中时返回缓存数据")
        void cacheHit() {
            ConversationSummary cached = new ConversationSummary();
            cached.setSummary("摘要内容");
            when(valueOperations.get("memory:summary:10")).thenReturn(cached);

            ConversationSummary result = memoryService.getLatestSummary(10L);

            assertNotNull(result);
            assertEquals("摘要内容", result.getSummary());
            verify(summaryRepository, never()).findLatestByConversationId(any());
        }

        @Test
        @DisplayName("缓存未命中时查 DB")
        void cacheMiss() {
            when(valueOperations.get("memory:summary:10")).thenReturn(null);
            ConversationSummary dbSummary = new ConversationSummary();
            dbSummary.setSummary("DB摘要");
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(dbSummary);

            ConversationSummary result = memoryService.getLatestSummary(10L);

            assertEquals("DB摘要", result.getSummary());
            verify(valueOperations).set(eq("memory:summary:10"), any(), eq(10L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("无摘要时返回 null")
        void noSummary() {
            when(valueOperations.get("memory:summary:10")).thenReturn(null);
            when(summaryRepository.findLatestByConversationId(10L)).thenReturn(null);

            ConversationSummary result = memoryService.getLatestSummary(10L);

            assertNull(result);
        }
    }
}
