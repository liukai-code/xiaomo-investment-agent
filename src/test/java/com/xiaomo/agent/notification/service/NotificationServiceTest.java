package com.xiaomo.agent.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.notification.entity.Notification;
import com.xiaomo.agent.notification.entity.NotificationRecipient;
import com.xiaomo.agent.notification.repository.NotificationRecipientRepository;
import com.xiaomo.agent.notification.repository.NotificationRepository;
import com.xiaomo.agent.notification.service.impl.NotificationServiceImpl;
import com.xiaomo.agent.user.entity.User;
import com.xiaomo.agent.user.repository.UserRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 通知服务测试")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationRecipientRepository notificationRecipientRepository;
    @Mock private UserRepository userRepository;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification testNotification;
    private User testUser;

    @BeforeEach
    void setUp() {
        testNotification = new Notification();
        testNotification.setId(1L);
        testNotification.setTitle("系统维护");
        testNotification.setContent("今晚22:00-23:00系统维护");
        testNotification.setCreatedAt(LocalDateTime.of(2026, 7, 11, 10, 0));
        testNotification.setBroadcast(true);

        testUser = new User();
        testUser.setId(100L);
        testUser.setEmail("test@example.com");
        testUser.setAccountId("user_100");
        testUser.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        // 通用 mock
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));
    }

    // ==================== create ====================

    @Nested
    @DisplayName("create 创建通知")
    class CreateTest {

        @Test
        @DisplayName("正常创建广播通知")
        void createBroadcastSuccess() throws Exception {
            when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1,\"title\":\"系统维护\",\"broadcast\":true}");

            Notification result = notificationService.create("系统维护", "今晚22:00-23:00系统维护");

            assertNotNull(result);
            assertEquals("系统维护", result.getTitle());
            assertEquals("今晚22:00-23:00系统维护", result.getContent());
            assertTrue(result.getBroadcast());
            verify(notificationRepository).save(any(Notification.class));
            verify(notificationRecipientRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("创建定向通知并保存接收人")
        void createTargetedSuccess() throws Exception {
            Notification targeted = new Notification();
            targeted.setId(2L);
            targeted.setTitle("定向通知");
            targeted.setContent("仅发给指定用户");
            targeted.setBroadcast(false);

            when(notificationRepository.save(any(Notification.class))).thenReturn(targeted);
            when(notificationRecipientRepository.saveAll(any())).thenReturn(List.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":2,\"broadcast\":false}");

            Notification result = notificationService.create("定向通知", "仅发给指定用户", List.of(100L, 200L));

            assertNotNull(result);
            assertFalse(result.getBroadcast());
            verify(notificationRecipientRepository).saveAll(any());
        }

        @Test
        @DisplayName("targetUserIds 为空列表时创建广播通知")
        void createWithEmptyTargetListIsBroadcast() throws Exception {
            when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"broadcast\":true}");

            Notification result = notificationService.create("通知", "内容", List.of());

            assertTrue(result.getBroadcast());
            verify(notificationRecipientRepository, never()).saveAll(any());
        }
    }

    // ==================== getTargetUsers ====================

    @Nested
    @DisplayName("getTargetUsers 获取接收人")
    class GetTargetUsersTest {

        @Test
        @DisplayName("返回通知的接收人 ID 列表")
        void returnsTargetUserIds() {
            NotificationRecipient r1 = new NotificationRecipient();
            r1.setNotificationId(1L);
            r1.setUserId(100L);
            NotificationRecipient r2 = new NotificationRecipient();
            r2.setNotificationId(1L);
            r2.setUserId(200L);

            when(notificationRecipientRepository.findByNotificationId(1L)).thenReturn(List.of(r1, r2));

            List<Long> result = notificationService.getTargetUsers(1L);

            assertEquals(2, result.size());
            assertTrue(result.contains(100L));
            assertTrue(result.contains(200L));
        }

        @Test
        @DisplayName("无接收人时返回空列表")
        void returnsEmptyList() {
            when(notificationRecipientRepository.findByNotificationId(99L)).thenReturn(List.of());

            List<Long> result = notificationService.getTargetUsers(99L);

            assertTrue(result.isEmpty());
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
        @DisplayName("删除通知时同时删除接收人记录")
        void deleteSuccess() {
            doNothing().when(notificationRecipientRepository).deleteByNotificationId(1L);
            doNothing().when(notificationRepository).deleteById(1L);

            assertDoesNotThrow(() -> notificationService.delete(1L));
            verify(notificationRecipientRepository).deleteByNotificationId(1L);
            verify(notificationRepository).deleteById(1L);
        }
    }

    // ==================== getUnreadCount ====================

    @Nested
    @DisplayName("getUnreadCount 获取未读数")
    class GetUnreadCountTest {

        @Test
        @DisplayName("有未读广播通知时返回正确数量")
        void hasUnreadBroadcastNotifications() {
            Notification n2 = new Notification();
            n2.setId(2L);
            n2.setTitle("通知2");
            n2.setBroadcast(true);

            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of());
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(n2, testNotification));
            when(setOperations.members("notification:read:100")).thenReturn(Set.of("1"));
            when(setOperations.members("notification:hidden:100")).thenReturn(null);

            long count = notificationService.getUnreadCount(100L);

            assertEquals(1, count);
        }

        @Test
        @DisplayName("有未读定向通知时也计入未读数")
        void hasUnreadTargetedNotifications() {
            NotificationRecipient r = new NotificationRecipient();
            r.setNotificationId(2L);
            r.setUserId(100L);

            Notification n2 = new Notification();
            n2.setId(2L);
            n2.setTitle("定向通知");
            n2.setBroadcast(false);

            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of(r));
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrIdInOrderByCreatedAtDesc(any(), any(), any()))
                    .thenReturn(List.of(testNotification, n2));
            when(setOperations.members("notification:read:100")).thenReturn(Set.of());
            when(setOperations.members("notification:hidden:100")).thenReturn(null);

            long count = notificationService.getUnreadCount(100L);

            assertEquals(2, count);
        }

        @Test
        @DisplayName("全部已读时返回 0")
        void allRead() {
            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of());
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(testNotification));
            when(setOperations.members("notification:read:100")).thenReturn(Set.of("1"));
            when(setOperations.members("notification:hidden:100")).thenReturn(null);

            long count = notificationService.getUnreadCount(100L);

            assertEquals(0, count);
        }

        @Test
        @DisplayName("无通知时返回 0")
        void noNotifications() {
            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of());
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any()))
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
            when(setOperations.members("notification:read:100")).thenReturn(Set.of("1", "3"));

            Set<Long> result = notificationService.getReadIds(100L);

            assertEquals(2, result.size());
            assertTrue(result.contains(1L));
            assertTrue(result.contains(3L));
        }

        @Test
        @DisplayName("无已读记录时返回空集合")
        void returnsEmptySet() {
            when(setOperations.members("notification:read:100")).thenReturn(null);

            Set<Long> result = notificationService.getReadIds(100L);

            assertTrue(result.isEmpty());
        }
    }

    // ==================== listRecentForUser ====================

    @Nested
    @DisplayName("listRecentForUser 用户通知列表")
    class ListRecentForUserTest {

        @Test
        @DisplayName("返回广播通知并过滤隐藏")
        void returnsBroadcastNotifications() {
            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of());
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(testNotification));
            when(setOperations.members("notification:hidden:100")).thenReturn(null);

            List<Notification> result = notificationService.listRecentForUser(100L, 50);

            assertEquals(1, result.size());
            assertEquals("系统维护", result.get(0).getTitle());
        }

        @Test
        @DisplayName("返回广播+定向通知")
        void returnsBroadcastAndTargetedNotifications() {
            NotificationRecipient r = new NotificationRecipient();
            r.setNotificationId(2L);
            r.setUserId(100L);

            Notification targeted = new Notification();
            targeted.setId(2L);
            targeted.setTitle("定向通知");
            targeted.setBroadcast(false);

            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of(r));
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrIdInOrderByCreatedAtDesc(any(), any(), any()))
                    .thenReturn(List.of(testNotification, targeted));
            when(setOperations.members("notification:hidden:100")).thenReturn(null);

            List<Notification> result = notificationService.listRecentForUser(100L, 50);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("过滤掉隐藏的通知")
        void filtersHiddenNotifications() {
            when(notificationRecipientRepository.findByUserId(100L)).thenReturn(List.of());
            when(notificationRepository.findAllByBroadcastTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(testNotification));
            when(setOperations.members("notification:hidden:100")).thenReturn(Set.of("1"));

            List<Notification> result = notificationService.listRecentForUser(100L, 50);

            assertTrue(result.isEmpty());
        }
    }
}
