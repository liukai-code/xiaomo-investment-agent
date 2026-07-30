package com.xiaomo.agent.agent.intent;

/**
 * 分析深度枚举。
 * "深度"描述的是执行强度，不是业务类型。
 * "深度分析茅台"的业务意图是 STOCK_ANALYSIS，深度是 DEEP。
 */
public enum AnalysisDepth {

    /** 普通分析 — 单步或少量工具调用即可完成 */
    NORMAL,

    /** 深度分析 — 需要 LLM 多步规划，综合多个数据源 */
    DEEP
}
