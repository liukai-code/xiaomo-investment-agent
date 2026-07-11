package com.itlk.myclaudecode.notification.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.notification.entity.Notification;
import com.itlk.myclaudecode.notification.service.NotificationService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNotificationController 管理员通知接口测试")
class AdminNotificationControllerTest {

    @Mock private NotificationService notificationService;

    @InjectMocks
    private AdminNotificationController controller;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = new Notification();
        testNotification.setId(1L);
        testNotification.setTitle("系统维护");
        testNotification.setContent("今晚22:00-23:00系统维护");
        testNotification.setCreatedAt(LocalDateTime.of(2026, 7, 11, 10, 0));
    }

    // ==================== create ====================

    @Nested
    @DisplayName("create 创建通知")
    class CreateTest {

        @Test
        @DisplayName("正常创建通知")
        void createSuccess() {
            when(notificationService.create("系统维护", "今晚维护")).thenReturn(testNotification);

            Result<Notification> result = controller.create(Map.of(
                    "title", "系统维护",
                    "content", "今晚维护"
            ));

            assertEquals(1, result.getCode());
            assertNotNull(result.getData());
            assertEquals("系统维护", result.getData().getTitle());
        }

        @Test
        @DisplayName("标题为空时返回错误")
        void createWithEmptyTitle() {
            Result<Notification> result = controller.create(Map.of("title", "", "content", "内容"));

            assertEquals(0, result.getCode());
            assertEquals("标题不能为空", result.getMsg());
            verify(notificationService, never()).create(any(), any());
        }

        @Test
        @DisplayName("标题为 null 时返回错误")
        void createWithNullTitle() {
            Result<Notification> result = controller.create(Map.of("content", "内容"));

            assertEquals(0, result.getCode());
            assertEquals("标题不能为空", result.getMsg());
        }

        @Test
        @DisplayName("内容为空时返回错误")
        void createWithEmptyContent() {
            Result<Notification> result = controller.create(Map.of("title", "标题", "content", ""));

            assertEquals(0, result.getCode());
            assertEquals("内容不能为空", result.getMsg());
            verify(notificationService, never()).create(any(), any());
        }
    }

    // ==================== list ====================

    @Nested
    @DisplayName("list 通知列表")
    class ListTest {

        @Test
        @DisplayName("返回全部通知列表")
        void listSuccess() {
            when(notificationService.listAll()).thenReturn(List.of(testNotification));

            Result<List<Notification>> result = controller.list();

            assertEquals(1, result.getCode());
            assertEquals(1, result.getData().size());
            assertEquals("系统维护", result.getData().get(0).getTitle());
        }

        @Test
        @DisplayName("无通知时返回空列表")
        void listEmpty() {
            when(notificationService.listAll()).thenReturn(List.of());

            Result<List<Notification>> result = controller.list();

            assertEquals(1, result.getCode());
            assertTrue(result.getData().isEmpty());
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("delete 删除通知")
    class DeleteTest {

        @Test
        @DisplayName("正常删除通知")
        void deleteSuccess() {
            doNothing().when(notificationService).delete(1L);

            Result<Void> result = controller.delete(1L);

            assertEquals(1, result.getCode());
            verify(notificationService).delete(1L);
        }
    }
}
