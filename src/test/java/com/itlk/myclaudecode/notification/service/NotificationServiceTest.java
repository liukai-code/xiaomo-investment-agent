package com.itlk.myclaudecode.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.notification.entity.Notification;
import com.itlk.myclaudecode.notification.repository.NotificationRepository;
import com.itlk.myclaudecode.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 通知服务测试")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

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
        @DisplayName("正常创建通知并保存到数据库")
        void createSuccess() throws Exception {
            when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1,\"title\":\"系统维护\"}");

            Notification result = notificationService.create("系统维护", "今晚22:00-23:00系统维护");

            assertNotNull(result);
            assertEquals("系统维护", result.getTitle());
            assertEquals("今晚22:00-23:00系统维护", result.getContent());
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    // ==================== listAll ====================

    @Nested
    @DisplayName("listAll 获取全部通知")
    class ListAllTest {

        @Test
        @DisplayName("返回按时间倒序的通知列表")
        void listAllReturnsDescOrder() {
            Notification n2 = new Notification();
            n2.setId(2L);
            n2.setTitle("新通知");
            n2.setCreatedAt(LocalDateTime.of(2026, 7, 11, 12, 0));

            when(notificationRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(n2, testNotification));

            List<Notification> result = notificationService.listAll();

            assertEquals(2, result.size());
            assertEquals("新通知", result.get(0).getTitle());
            assertEquals("系统维护", result.get(1).getTitle());
        }

        @Test
        @DisplayName("无通知时返回空列表")
        void listAllReturnsEmpty() {
            when(notificationRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of());

            List<Notification> result = notificationService.listAll();

            assertTrue(result.isEmpty());
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("delete 删除通知")
    class DeleteTest {

        @Test
        @DisplayName("正常删除通知")
        void deleteSuccess() {
            doNothing().when(notificationRepository).deleteById(1L);

            assertDoesNotThrow(() -> notificationService.delete(1L));
            verify(notificationRepository).deleteById(1L);
        }
    }

    // ==================== getUnreadCount ====================

    @Nested
    @DisplayName("getUnreadCount 获取未读数")
    class GetUnreadCountTest {

        @Test
        @DisplayName("有未读通知时返回正确数量")
        void hasUnreadNotifications() {
            Notification n2 = new Notification();
            n2.setId(2L);
            n2.setTitle("通知2");

            when(notificationRepository.findAllByOrderByCreatedAtDesc(any()))
                    .thenReturn(List.of(n2, testNotification));
            when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("notification:read:100")).thenReturn(Set.of("1"));

            long count = notificationService.getUnreadCount(100L);

            assertEquals(1, count);
        }

        @Test
        @DisplayName("全部已读时返回 0")
        void allRead() {
            when(notificationRepository.findAllByOrderByCreatedAtDesc(any()))
                    .thenReturn(List.of(testNotification));
            when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("notification:read:100")).thenReturn(Set.of("1"));

            long count = notificationService.getUnreadCount(100L);

            assertEquals(0, count);
        }

        @Test
        @DisplayName("无通知时返回 0")
        void noNotifications() {
            when(notificationRepository.findAllByOrderByCreatedAtDesc(any()))
                    .thenReturn(List.of());

            long count = notificationService.getUnreadCount(100L);

            assertEquals(0, count);
        }
    }

    // ==================== markAsRead ====================

    @Nested
    @DisplayName("markAsRead 标记已读")
    class MarkAsReadTest {

        @Test
        @DisplayName("正常标记通知为已读")
        void markAsReadSuccess() {
            when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.add("notification:read:100", "1")).thenReturn(1L);

            assertDoesNotThrow(() -> notificationService.markAsRead(100L, 1L));
            verify(setOperations).add("notification:read:100", "1");
        }
    }

    // ==================== getReadIds ====================

    @Nested
    @DisplayName("getReadIds 获取已读 ID")
    class GetReadIdsTest {

        @Test
        @DisplayName("返回用户已读的通知 ID 集合")
        void returnsReadIds() {
            when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("notification:read:100")).thenReturn(Set.of("1", "3"));

            Set<Long> result = notificationService.getReadIds(100L);

            assertEquals(2, result.size());
            assertTrue(result.contains(1L));
            assertTrue(result.contains(3L));
        }

        @Test
        @DisplayName("无已读记录时返回空集合")
        void returnsEmptySet() {
            when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("notification:read:100")).thenReturn(null);

            Set<Long> result = notificationService.getReadIds(100L);

            assertTrue(result.isEmpty());
        }
    }
}
