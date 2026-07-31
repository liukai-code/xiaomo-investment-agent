package com.xiaomo.agent.agent.intent;

/**
 * 从用户请求中提取的结构化特征，用于判断执行模式。
 *
 * @param targetCount              标的数量（股票、板块等）
 * @param dimensionCount           信息维度数量（估值、基本面、资金面等）
 * @param subGoalCount             子目标数量
 * @param estimatedToolCalls       预估工具调用次数
 * @param hasDependentSteps        是否存在前后依赖（"先...再..."等）
 * @param hasSynthesisRequirement  是否需要综合决策（"是否值得买"等）
 * @param hasComparisonRequirement 是否需要对比（"比较"、"对比"等）
 */
public record RequestFeatures(
        int targetCount,
        int dimensionCount,
        int subGoalCount,
        int estimatedToolCalls,
        boolean hasDependentSteps,
        boolean hasSynthesisRequirement,
        boolean hasComparisonRequirement
) {
}
