package com.xiaomo.agent.user.config;

import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.common.util.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserConfigService 渠道管理测试")
class UserConfigServiceTest {

    @Mock private UserConfigRepository userConfigRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HttpClientService httpClientService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ToolCallingManager toolCallingManager;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private UserConfigService userConfigService;

    private UserConfig sampleConfig;

    @BeforeEach
    void setUp() {
        sampleConfig = new UserConfig();
        sampleConfig.setId(1L);
        sampleConfig.setUserId(100L);
        sampleConfig.setChannelName("test-channel");
        sampleConfig.setApiKeyEncrypted("encrypted-key");
        sampleConfig.setBaseUrl("https://api.example.com");
        sampleConfig.setModelName("claude-sonnet-4-6");
        sampleConfig.setIsActive(true);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== listChannels ====================

    @Nested
    @DisplayName("listChannels 列出渠道")
    class ListChannelsTest {

        @Test
        @DisplayName("返回渠道列表和激活渠道ID")
        void returnsChannelListWithActiveId() {
            UserConfig ch2 = new UserConfig();
            ch2.setId(2L);
            ch2.setUserId(100L);
            ch2.setChannelName("ch2");
            ch2.setIsActive(false);

            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L))
                    .thenReturn(List.of(sampleConfig, ch2));

            var result = userConfigService.listChannels(100L);

            assertEquals(2, result.getChannels().size());
            assertEquals(1L, result.getActiveChannelId());
            assertEquals("test-channel", result.getChannels().get(0).getChannelName());
            assertTrue(result.getChannels().get(0).isActive());
            assertFalse(result.getChannels().get(1).isActive());
        }

        @Test
        @DisplayName("无渠道时返回空列表")
        void returnsEmptyListWhenNoChannels() {
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L))
                    .thenReturn(Collections.emptyList());

            var result = userConfigService.listChannels(100L);

            assertTrue(result.getChannels().isEmpty());
            assertNull(result.getActiveChannelId());
        }
    }

    // ==================== createChannel ====================

    @Nested
    @DisplayName("createChannel 创建渠道")
    class CreateChannelTest {

        @Test
        @DisplayName("首个渠道自动激活")
        void firstChannelAutoActivated() {
            ApiChannelDTO dto = new ApiChannelDTO();
            dto.setChannelName("new-channel");
            dto.setApiKey("sk-1234567890");
            dto.setBaseUrl("https://api.test.com");
            dto.setModelName("claude-sonnet-4-6");

            when(userConfigRepository.existsByUserIdAndChannelName(100L, "new-channel")).thenReturn(false);
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L)).thenReturn(Collections.emptyList());
            when(encryptionService.encrypt("sk-1234567890")).thenReturn("encrypted");
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> {
                UserConfig c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            var result = userConfigService.createChannel(100L, dto);

            assertEquals("new-channel", result.getChannelName());
            assertTrue(result.isActive());
            verify(userConfigRepository).save(any(UserConfig.class));
        }

        @Test
        @DisplayName("非首个渠道不自动激活")
        void nonFirstChannelNotActivated() {
            ApiChannelDTO dto = new ApiChannelDTO();
            dto.setChannelName("second-channel");
            dto.setApiKey("sk-1234567890");

            when(userConfigRepository.existsByUserIdAndChannelName(100L, "second-channel")).thenReturn(false);
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(sampleConfig));
            when(encryptionService.encrypt("sk-1234567890")).thenReturn("encrypted");
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = userConfigService.createChannel(100L, dto);

            assertFalse(result.isActive());
        }

        @Test
        @DisplayName("渠道名重复时抛异常")
        void throwsOnDuplicateName() {
            ApiChannelDTO dto = new ApiChannelDTO();
            dto.setChannelName("test-channel");
            dto.setApiKey("sk-1234567890");

            when(userConfigRepository.existsByUserIdAndChannelName(100L, "test-channel")).thenReturn(true);

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> userConfigService.createChannel(100L, dto));
            assertEquals("渠道名称已存在", ex.getMessage());
        }
    }

    // ==================== activateChannel ====================

    @Nested
    @DisplayName("activateChannel 激活渠道")
    class ActivateChannelTest {

        @Test
        @DisplayName("激活指定渠道并取消其他渠道")
        void activatesTargetAndDeactivatesOthers() {
            when(userConfigRepository.findByIdAndUserId(2L, 100L)).thenReturn(Optional.of(sampleConfig));
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            userConfigService.activateChannel(100L, 2L);

            verify(userConfigRepository).deactivateAllByUserId(100L);
            verify(userConfigRepository).save(argThat(c -> Boolean.TRUE.equals(c.getIsActive())));
        }

        @Test
        @DisplayName("渠道不存在时抛异常")
        void throwsWhenChannelNotFound() {
            when(userConfigRepository.findByIdAndUserId(999L, 100L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> userConfigService.activateChannel(100L, 999L));
        }
    }

    // ==================== deleteChannel ====================

    @Nested
    @DisplayName("deleteChannel 删除渠道")
    class DeleteChannelTest {

        @Test
        @DisplayName("删除非激活渠道不触发自动切换")
        void deleteInactiveChannelNoSwitch() {
            sampleConfig.setIsActive(false);
            when(userConfigRepository.findByIdAndUserId(1L, 100L)).thenReturn(Optional.of(sampleConfig));

            userConfigService.deleteChannel(100L, 1L);

            verify(userConfigRepository).deleteByIdAndUserId(1L, 100L);
            verify(userConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("删除激活渠道后自动切换到下一个")
        void deleteActiveChannelAutoSwitches() {
            UserConfig remaining = new UserConfig();
            remaining.setId(2L);
            remaining.setIsActive(false);

            when(userConfigRepository.findByIdAndUserId(1L, 100L)).thenReturn(Optional.of(sampleConfig));
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(remaining));
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            userConfigService.deleteChannel(100L, 1L);

            verify(userConfigRepository).deleteByIdAndUserId(1L, 100L);
            verify(userConfigRepository).save(argThat(c -> c.getId().equals(2L) && Boolean.TRUE.equals(c.getIsActive())));
        }

        @Test
        @DisplayName("渠道不存在时抛异常")
        void throwsWhenNotFound() {
            when(userConfigRepository.findByIdAndUserId(999L, 100L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> userConfigService.deleteChannel(100L, 999L));
        }
    }

    // ==================== findEffectiveConfig ====================

    @Nested
    @DisplayName("findEffectiveConfig 有效配置回退")
    class FindEffectiveConfigTest {

        @Test
        @DisplayName("优先返回激活渠道")
        void returnsActiveChannel() {
            when(userConfigRepository.findByUserIdAndIsActiveTrue(100L))
                    .thenReturn(Optional.of(sampleConfig));

            var result = userConfigService.getConfig(100L);

            assertNotNull(result);
            assertEquals("https://api.example.com", result.getBaseUrl());
        }

        @Test
        @DisplayName("无激活渠道时回退到第一条并标记激活")
        void fallsBackToFirstAndMarksActive() {
            sampleConfig.setIsActive(null);
            when(userConfigRepository.findByUserIdAndIsActiveTrue(100L)).thenReturn(Optional.empty());
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(sampleConfig));
            when(userConfigRepository.save(any(UserConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = userConfigService.getConfig(100L);

            assertNotNull(result);
            verify(userConfigRepository).save(argThat(c -> Boolean.TRUE.equals(c.getIsActive())));
        }

        @Test
        @DisplayName("无任何配置时返回null")
        void returnsNullWhenNoConfig() {
            when(userConfigRepository.findByUserIdAndIsActiveTrue(100L)).thenReturn(Optional.empty());
            when(userConfigRepository.findByUserIdOrderByCreatedAtAsc(100L)).thenReturn(Collections.emptyList());

            var result = userConfigService.getConfig(100L);

            assertNull(result);
        }
    }
}
