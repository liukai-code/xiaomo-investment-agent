package com.xiaomo.agent.agent.intent;

/**
 * 工具策略模式。
 * 替代原先 suggestedTools 的三态语义（null / 空集 / 非空集）。
 */
public enum ToolPolicyMode {

    /** 白名单模式 — 只加载 tools 中指定的工具 */
    ALLOW_LIST,

    /** 禁用所有业务工具 — 通用对话模式，LLM 用自身知识回答 */
    DENY_ALL,

    /** 由 Planner 管理 — 跳过静态意图白名单，由 LLM 根据执行计划动态选择工具 */
    PLANNER_MANAGED
}
