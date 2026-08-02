package com.xiaomo.agent.user.dto;

import com.xiaomo.agent.user.entity.User;

public record UserPreferences(
        Long userId,
        String accountId,
        String email,
        double temperature,
        int maxTokens,
        int contextWindow,
        boolean memoryEnabled,
        boolean compressionEnabled
) {
    public static UserPreferences fromEntity(User user) {
        return new UserPreferences(
                user.getId(),
                user.getAccountId(),
                user.getEmail(),
                user.getTemperature() != null ? user.getTemperature() : 0.7,
                user.getMaxTokens() != null ? user.getMaxTokens() : 4096,
                user.getContextWindow() != null ? user.getContextWindow() : 50,
                user.getMemoryEnabled() == null || user.getMemoryEnabled(),
                user.getCompressionEnabled() == null || user.getCompressionEnabled()
        );
    }
}
