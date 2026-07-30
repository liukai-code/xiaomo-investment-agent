package com.xiaomo.agent.agent.intent;

/**
 * 用户意图类型枚举。
 * 只描述业务意图（用户想分析什么），不描述执行方式（怎么分析）。
 * <p>
 * "深度分析"不是独立的业务意图，而是通过 AnalysisDepth.DEEP 标记。
 * 例如"深度分析茅台" → intent=STOCK_ANALYSIS, depth=DEEP。
 */
public enum IntentType {

    /** 个股分析 —— 已锁定标的，需要全量A股工具 */
    STOCK_ANALYSIS,

    /** 市场新闻资讯 —— 无具体标的，需要新闻+搜索工具 */
    MARKET_NEWS,

    /** 板块/行业分析 —— 板块级查询，不触发标的守卫 */
    SECTOR_ANALYSIS,

    /** 打板/情绪 —— 涨停、跌停、连板、市场情绪 */
    TRADING_SENTIMENT,

    /** 持仓查询 —— "我的基金"、"我的持仓" */
    HOLDINGS_QUERY,

    /** 金融计算 —— 用户明确要求数值计算 */
    FINANCIAL_CALC,

    /** 数据库查询 —— SQL 查询 */
    DB_QUERY,

    /** 通用对话 —— 问候、概念解释、闲聊，不需要工具 */
    GENERAL_CHAT
}
