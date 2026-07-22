package com.xiaomo.agent.memory.controller;

import com.xiaomo.agent.common.entity.Result;
import com.xiaomo.agent.memory.dto.ProfileDTO;
import com.xiaomo.agent.memory.entity.*;
import com.xiaomo.agent.memory.service.MemoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryController 记忆管理接口测试")
class MemoryControllerTest {

    @Mock private MemoryService memoryService;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private MemoryController memoryController;

    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        testProfile = new UserProfile();
        testProfile.setId(1L);
        testProfile.setUserId(100L);
        testProfile.setCategory(ProfileCategory.INVESTMENT_STYLE);
        testProfile.setContent("偏好价值投资");
        testProfile.setImportance(5);
        testProfile.setSourceType(MemorySourceType.USER_EXPLICIT);
        testProfile.setActive(true);
        testProfile.setCreatedAt(LocalDateTime.of(2026, 7, 21, 10, 0));
        testProfile.setUpdatedAt(LocalDateTime.of(2026, 7, 21, 10, 0));

        lenient().when(request.getAttribute("userId")).thenReturn(100L);
    }

    // ==================== listProfiles ====================

    @Nested
    @DisplayName("listProfiles 获取画像列表")
    class ListProfilesTest {

        @Test
        @DisplayName("正常返回画像列表")
        void success() {
            when(memoryService.getActiveProfiles(100L)).thenReturn(List.of(testProfile));

            Result<List<ProfileDTO>> result = memoryController.listProfiles(request);

            assertEquals(1, result.getCode());
            assertEquals(1, result.getData().size());
            assertEquals("偏好价值投资", result.getData().get(0).getContent());
            assertEquals(ProfileCategory.INVESTMENT_STYLE, result.getData().get(0).getCategory());
        }

        @Test
        @DisplayName("未登录时抛异常")
        void notLoggedIn() {
            when(request.getAttribute("userId")).thenReturn(null);

            assertThrows(RuntimeException.class, () -> memoryController.listProfiles(request));
        }
    }

    // ==================== addProfile ====================

    @Nested
    @DisplayName("addProfile 添加画像记忆")
    class AddProfileTest {

        @Test
        @DisplayName("正常添加记忆")
        void success() {
            ProfileDTO dto = new ProfileDTO();
            dto.setContent("我是短线交易者");
            dto.setCategory(ProfileCategory.INVESTMENT_STYLE);

            UserProfile saved = new UserProfile();
            saved.setId(2L);
            saved.setUserId(100L);
            saved.setContent("我是短线交易者");
            saved.setCategory(ProfileCategory.INVESTMENT_STYLE);
            saved.setImportance(5);
            saved.setSourceType(MemorySourceType.USER_EXPLICIT);
            saved.setActive(true);

            when(memoryService.addUserMemory(eq(100L), eq("我是短线交易者"),
                    eq(ProfileCategory.INVESTMENT_STYLE), isNull())).thenReturn(saved);

            Result<ProfileDTO> result = memoryController.addProfile(dto, request);

            assertEquals(1, result.getCode());
            assertEquals("我是短线交易者", result.getData().getContent());
        }

        @Test
        @DisplayName("内容为空时返回错误")
        void emptyContent() {
            ProfileDTO dto = new ProfileDTO();
            dto.setContent("");
            dto.setCategory(ProfileCategory.GENERAL);

            Result<ProfileDTO> result = memoryController.addProfile(dto, request);

            assertEquals(0, result.getCode());
            assertTrue(result.getMsg().contains("不能为空"));
        }

        @Test
        @DisplayName("类别为空时返回错误")
        void nullCategory() {
            ProfileDTO dto = new ProfileDTO();
            dto.setContent("有内容");
            dto.setCategory(null);

            Result<ProfileDTO> result = memoryController.addProfile(dto, request);

            assertEquals(0, result.getCode());
            assertTrue(result.getMsg().contains("类别"));
        }
    }

    // ==================== updateProfile ====================

    @Nested
    @DisplayName("updateProfile 更新画像记忆")
    class UpdateProfileTest {

        @Test
        @DisplayName("正常更新")
        void success() {
            ProfileDTO dto = new ProfileDTO();
            dto.setContent("更新后的内容");
            dto.setImportance(4);

            UserProfile updated = new UserProfile();
            updated.setId(1L);
            updated.setUserId(100L);
            updated.setContent("更新后的内容");
            updated.setImportance(4);

            when(memoryService.updateProfile(100L, 1L, "更新后的内容", 4)).thenReturn(updated);

            Result<ProfileDTO> result = memoryController.updateProfile(1L, dto, request);

            assertEquals(1, result.getCode());
            assertEquals("更新后的内容", result.getData().getContent());
            assertEquals(4, result.getData().getImportance());
        }
    }

    // ==================== deleteProfile ====================

    @Nested
    @DisplayName("deleteProfile 删除画像记忆")
    class DeleteProfileTest {

        @Test
        @DisplayName("正常删除")
        void success() {
            doNothing().when(memoryService).deleteProfile(100L, 1L);

            Result<Void> result = memoryController.deleteProfile(1L, request);

            assertEquals(1, result.getCode());
            verify(memoryService).deleteProfile(100L, 1L);
        }
    }

    // ==================== getSummary ====================

    @Nested
    @DisplayName("getSummary 获取对话摘要")
    class GetSummaryTest {

        @Test
        @DisplayName("有摘要时返回")
        void withSummary() {
            ConversationSummary summary = new ConversationSummary();
            summary.setId(1L);
            summary.setConversationId(10L);
            summary.setSummary("用户询问茅台估值，建议长期持有");
            when(memoryService.getLatestSummary(10L)).thenReturn(summary);

            Result<ConversationSummary> result = memoryController.getSummary(10L, request);

            assertEquals(1, result.getCode());
            assertEquals("用户询问茅台估值，建议长期持有", result.getData().getSummary());
        }

        @Test
        @DisplayName("无摘要时返回 null data")
        void noSummary() {
            when(memoryService.getLatestSummary(10L)).thenReturn(null);

            Result<ConversationSummary> result = memoryController.getSummary(10L, request);

            assertEquals(1, result.getCode());
            assertNull(result.getData());
        }
    }
}
