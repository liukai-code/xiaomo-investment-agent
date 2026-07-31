package com.xiaomo.agent.agent.intent;

/**
 * 执行模式 — 决定 Agent 如何处理用户请求。
 *
 * <ul>
 *   <li>DIRECT — 单次工具调用或直接回答，无需规划</li>
 *   <li>PARALLEL — 多个独立工具调用，可并行执行，无需规划</li>
 *   <li>PLANNING — 存在任务拆解、执行依赖或动态决策，需要 LLM 生成执行计划</li>
 * </ul>
 */
public enum ExecutionMode {

    /** 单次工具调用或直接回答 */
    DIRECT,

    /** 多个独立工具调用，可并行执行 */
    PARALLEL,

    /** 存在任务拆解、执行依赖或动态决策 */
    PLANNING
}
