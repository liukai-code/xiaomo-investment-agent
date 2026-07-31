package com.xiaomo.agent.agent.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        ChatStreamEventType type,
        String toolName,
        String operation,
        String content,
        Integer step,
        Integer totalSteps,
        String planGoal,
        List<PlanStepDto> planSteps
) {
    public enum ChatStreamEventType {
        THINKING,
        TOOL_CALL,
        TOOL_RESULT,
        CONTENT,
        PLAN
    }

    public record PlanStepDto(int id, String action, String tool) {}

    public static ChatStreamEvent thinking() {
        return new ChatStreamEvent(ChatStreamEventType.THINKING, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent toolCall(String toolName, String operation, int step, int totalSteps) {
        return new ChatStreamEvent(ChatStreamEventType.TOOL_CALL, toolName, operation, null, step, totalSteps, null, null);
    }

    public static ChatStreamEvent toolResult(String toolName) {
        return new ChatStreamEvent(ChatStreamEventType.TOOL_RESULT, toolName, null, null, null, null, null, null);
    }

    public static ChatStreamEvent plan(String goal, List<PlanStepDto> steps) {
        return new ChatStreamEvent(ChatStreamEventType.PLAN, null, null, null, null, null, goal, steps);
    }
}
