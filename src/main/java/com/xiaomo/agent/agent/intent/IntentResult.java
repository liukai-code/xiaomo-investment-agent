package com.xiaomo.agent.agent.intent;

import java.util.Set;

/**
 * 意图分类结果
 *
 * @param intent         分类出的意图类型
 * @param confidence     分类置信度 0.0-1.0
 * @param target         解析出的标的信息（仅 STOCK_ANALYSIS 时非 null）
 * @param suggestedTools 该意图建议启用的工具名白名单（null 表示使用全部已启用工具，空集表示无工具）
 */
public record IntentResult(
        IntentType intent,
        double confidence,
        ResolvedTarget target,
        Set<String> suggestedTools
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
