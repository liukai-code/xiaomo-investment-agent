package com.xiaomo.agent.agent.service.impl;

import java.util.List;

/**
 * Agent 自主任务规划的执行计划。
 * @param goal      任务目标概述
 * @param steps     执行步骤列表
 * @param planPrompt 格式化后的计划文本，注入 system prompt 引导 LLM 按计划执行
 */
public record PlanContext(
        String goal,
        List<PlanStep> steps,
        String planPrompt
) {
    public record PlanStep(int id, String action, String tool, String argsHint) {}
}
