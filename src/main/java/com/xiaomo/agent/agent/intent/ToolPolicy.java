package com.xiaomo.agent.agent.intent;

import java.util.Set;

/**
 * 工具策略。
 *
 * @param mode  策略模式
 * @param tools 白名单工具集合（仅 ALLOW_LIST 模式下有效）
 */
public record ToolPolicy(ToolPolicyMode mode, Set<String> tools) {

    public static ToolPolicy allowList(Set<String> tools) {
        return new ToolPolicy(ToolPolicyMode.ALLOW_LIST, tools);
    }

    public static ToolPolicy denyAll() {
        return new ToolPolicy(ToolPolicyMode.DENY_ALL, Set.of());
    }

    public static ToolPolicy plannerManaged() {
        return new ToolPolicy(ToolPolicyMode.PLANNER_MANAGED, null);
    }

    /**
     * 提取白名单集合供 ToolFilter 使用。
     * PLANNER_MANAGED 返回 null 表示不过滤。
     */
    public Set<String> toWhitelist() {
        return switch (mode) {
            case ALLOW_LIST -> tools;
            case DENY_ALL -> Set.of();
            case PLANNER_MANAGED -> null;
        };
    }
}
