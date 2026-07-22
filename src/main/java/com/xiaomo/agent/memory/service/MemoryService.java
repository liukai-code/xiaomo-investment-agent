package com.xiaomo.agent.memory.service;

import com.xiaomo.agent.memory.entity.ConversationSummary;
import com.xiaomo.agent.memory.entity.ProfileCategory;
import com.xiaomo.agent.memory.entity.UserProfile;

import java.util.List;

public interface MemoryService {

    // ====== 用户画像记忆 ======

    List<UserProfile> getActiveProfiles(Long userId);

    List<UserProfile> getProfilesByCategory(Long userId, ProfileCategory category);

    UserProfile addUserMemory(Long userId, String content, ProfileCategory category, Long conversationId);

    UserProfile updateProfile(Long userId, Long profileId, String content, Integer importance);

    void deleteProfile(Long userId, Long profileId);

    // ====== 对话摘要 ======

    ConversationSummary getLatestSummary(Long conversationId);

    ConversationSummary saveSummary(Long userId, Long conversationId, String summary,
                                    Long fromMessageId, Long toMessageId, int compressedCount);

    // ====== System Prompt 注入 ======

    String buildMemoryPrompt(Long userId, Long conversationId);

    // ====== 用户主动触发检测 ======

    DetectResult detectExplicitMemory(String message);

    record DetectResult(boolean detected, String content, ProfileCategory category) {}
}
