package com.xiaomo.agent.agent.intent;

import java.util.Map;
import java.util.Set;

/**
 * 意图→工具白名单映射。
 * <p>
 * 设计原则：
 * 1. 工具名与 ToolDefinition.name() 一致（即 @Tool 注解的方法名）
 * 2. PLANNER_MANAGED 模式跳过意图级过滤，由 Planner 管理工具选择
 * 3. DENY_ALL 模式不加载任何业务工具
 * 4. MCP 工具（搜索类）由 AgentLoopImpl 在过滤时特殊处理，始终保留
 */
public final class IntentToolGroupMap {

    // ===== A股全量工具（个股分析） =====
    private static final Set<String> ASTOCK_FULL_TOOLS = Set.of(
            "a_stock_quote",
            "a_stock_report",
            "a_stock_signal",
            "a_stock_capital",
            "a_stock_news",
            "a_stock_limit_up",
            "a_stock_option",
            "a_stock_sentiment",
            "market_data",
            "financial_calculator",
            "getAnalysisReport",
            "fetchWebpage",
            "fetchArticleContent"
    );

    // ===== 新闻+搜索工具（市场新闻） =====
    private static final Set<String> NEWS_TOOLS = Set.of(
            "a_stock_news",
            "a_stock_signal",
            "a_stock_limit_up",
            "market_data",
            "fetchWebpage",
            "fetchArticleContent"
    );

    // ===== 板块/行业分析工具 =====
    private static final Set<String> SECTOR_TOOLS = Set.of(
            "a_stock_signal",
            "a_stock_report",
            "a_stock_news",
            "a_stock_limit_up",
            "a_stock_sentiment",
            "market_data",
            "fetchWebpage",
            "fetchArticleContent"
    );

    // ===== 打板/情绪工具 =====
    private static final Set<String> SENTIMENT_TOOLS = Set.of(
            "a_stock_limit_up",
            "a_stock_signal",
            "a_stock_sentiment",
            "a_stock_news",
            "market_data"
    );

    // ===== 持仓查询工具 =====
    private static final Set<String> HOLDINGS_TOOLS = Set.of(
            "getMyHoldings",
            "getMyAccountSummary",
            "market_data",
            "a_stock_quote"
    );

    // ===== 金融计算工具 =====
    private static final Set<String> CALC_TOOLS = Set.of(
            "financial_calculator",
            "market_data"
    );

    // ===== 数据库查询工具 =====
    private static final Set<String> DB_TOOLS = Set.of(
            "getDatabaseSchema",
            "executeQuery"
    );

    private static final Map<IntentType, Set<String>> GROUP_MAP;

    static {
        Map<IntentType, Set<String>> m = new java.util.EnumMap<>(IntentType.class);
        m.put(IntentType.STOCK_ANALYSIS, ASTOCK_FULL_TOOLS);
        m.put(IntentType.MARKET_NEWS, NEWS_TOOLS);
        m.put(IntentType.SECTOR_ANALYSIS, SECTOR_TOOLS);
        m.put(IntentType.TRADING_SENTIMENT, SENTIMENT_TOOLS);
        m.put(IntentType.HOLDINGS_QUERY, HOLDINGS_TOOLS);
        m.put(IntentType.FINANCIAL_CALC, CALC_TOOLS);
        m.put(IntentType.DB_QUERY, DB_TOOLS);
        m.put(IntentType.GENERAL_CHAT, Set.of());
        GROUP_MAP = m;
    }

    /**
     * 根据业务意图和执行模式生成工具策略。
     * PLANNING / PARALLEL 模式返回 PLANNER_MANAGED，允许所有工具；
     * DIRECT 模式走静态白名单。
     */
    public static ToolPolicy getPolicy(IntentType intent, AnalysisDepth depth, ExecutionMode mode) {
        if (mode == ExecutionMode.PLANNING || mode == ExecutionMode.PARALLEL) {
            return ToolPolicy.plannerManaged();
        }
        Set<String> tools = GROUP_MAP.getOrDefault(intent, Set.of());
        if (tools.isEmpty() && intent == IntentType.GENERAL_CHAT) {
            return ToolPolicy.denyAll();
        }
        return ToolPolicy.allowList(tools);
    }

    private IntentToolGroupMap() {
    }
}
