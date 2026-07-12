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
import java.util.HashMap;
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
        testNotification.setBroadcast(true);
    }

    // ==================== create ====================

    @Nested
    @DisplayName("create 创建通知")
    class CreateTest {

        @Test
        @DisplayName("正常创建广播通知")
        void createSuccess() {
            when(notificationService.create("系统维护", "今晚维护", null)).thenReturn(testNotification);

            Map<String, Object> body = new HashMap<>();
            body.put("title", "系统维护");
            body.put("content", "今晚维护");

            Result<Notification> result = controller.create(body);

            assertEquals(1, result.getCode());
            assertNotNull(result.getData());
            assertEquals("系统维护", result.getData().getTitle());
            assertTrue(result.getData().getBroadcast());
        }

        @Test
        @DisplayName("创建定向通知")
        void createTargetedSuccess() {
            Notification targeted = new Notification();
            targeted.setId(2L);
            targeted.setTitle("定向通知");
            targeted.setContent("仅发给指定用户");
            targeted.setBroadcast(false);

            when(notificationService.create("定向通知", "仅发给指定用户", List.of(100L, 200L))).thenReturn(targeted);

            Map<String, Object> body = new HashMap<>();
            body.put("title", "定向通知");
            body.put("content", "仅发给指定用户");
            body.put("targetUserIds", List.of(100, 200)); // JSON 中数字通常是 Integer

            Result<Notification> result = controller.create(body);

            assertEquals(1, result.getCode());
            assertFalse(result.getData().getBroadcast());
        }

        @Test
        @DisplayName("标题为空时返回错误")
        void createWithEmptyTitle() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "");
            body.put("content", "内容");

            Result<Notification> result = controller.create(body);

            assertEquals(0, result.getCode());
            assertEquals("标题不能为空", result.getMsg());
            verify(notificationService, never()).create(any(), any(), any());
        }

        @Test
        @DisplayName("标题为 null 时返回错误")
        void createWithNullTitle() {
            Map<String, Object> body = new HashMap<>();
            body.put("content", "内容");

            Result<Notification> result = controller.create(body);

            assertEquals(0, result.getCode());
            assertEquals("标题不能为空", result.getMsg());
        }

        @Test
        @DisplayName("内容为空时返回错误")
        void createWithEmptyContent() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "标题");
            body.put("content", "");

            Result<Notification> result = controller.create(body);

            assertEquals(0, result.getCode());
            assertEquals("内容不能为空", result.getMsg());
            verify(notificationService, never()).create(any(), any(), any());
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

    // ==================== recipients ====================

    @Nested
    @DisplayName("recipients 获取接收人")
    class RecipientsTest {

        @Test
        @DisplayName("返回定向通知的接收人列表")
        void returnsRecipients() {
            when(notificationService.getTargetUsers(1L)).thenReturn(List.of(100L, 200L));

            Result<List<Long>> result = controller.recipients(1L);

            assertEquals(1, result.getCode());
            assertEquals(2, result.getData().size());
            assertTrue(result.getData().contains(100L));
            assertTrue(result.getData().contains(200L));
        }

        @Test
        @DisplayName("广播通知返回空列表")
        void returnsEmptyForBroadcast() {
            when(notificationService.getTargetUsers(1L)).thenReturn(List.of());

            Result<List<Long>> result = controller.recipients(1L);

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
