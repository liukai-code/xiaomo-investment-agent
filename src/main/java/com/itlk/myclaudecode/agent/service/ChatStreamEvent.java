package com.itlk.myclaudecode.agent.service;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        ChatStreamEventType type,
        String toolName,
        String content,
        Integer step,
        Integer totalSteps
) {
    public enum ChatStreamEventType {
        THINKING,
        TOOL_CALL,
        TOOL_RESULT,
        CONTENT
    }

    public static ChatStreamEvent thinking() {
        return new ChatStreamEvent(ChatStreamEventType.THINKING, null, null, null, null);
    }

    public static ChatStreamEvent toolCall(String toolName, int step, int totalSteps) {
        return new ChatStreamEvent(ChatStreamEventType.TOOL_CALL, toolName, null, step, totalSteps);
    }

    public static ChatStreamEvent toolResult(String toolName) {
        return new ChatStreamEvent(ChatStreamEventType.TOOL_RESULT, toolName, null, null, null);
    }
}
