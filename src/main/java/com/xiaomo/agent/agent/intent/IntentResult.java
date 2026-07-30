package com.xiaomo.agent.agent.intent;

/**
 * 意图分类结果
 *
 * @param intent     业务意图类型（个股分析、板块分析、新闻等）
 * @param depth      分析深度（普通 / 深度）
 * @param confidence 分类置信度 0.0-1.0
 * @param target     解析出的标的信息（需要标的的意图且解析成功时非 null）
 * @param policy     工具策略（白名单 / 禁用 / Planner 管理）
 */
public record IntentResult(
        IntentType intent,
        AnalysisDepth depth,
        double confidence,
        ResolvedTarget target,
        ToolPolicy policy
) {
    /**
     * 解析出的标的
     *
     * @param code 6位股票代码
     * @param name 股票名称（可为 null）
     */
    public record ResolvedTarget(String code, String name) {
    }
}
