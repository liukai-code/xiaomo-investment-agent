package com.itlk.myclaudecode.agent.intent;

import com.itlk.myclaudecode.common.config.HttpClientService;
import com.itlk.myclaudecode.workflow.util.StockResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则优先的意图分类器。
 * <p>
 * 分类策略：
 * 1. 优先匹配高特异性意图（持仓、通用对话、深度分析）
 * 2. 再匹配金融专业意图（新闻、板块、打板、计算、DB）
 * 3. 最后匹配个股分析（需要标的解析，成本最高）
 * 4. 兜底为 GENERAL_CHAT
 */
@Component
@Slf4j
public class RuleBasedIntentClassifier implements IntentClassifier {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private final HttpClientService httpClientService;
    private final boolean enabled;

    public RuleBasedIntentClassifier(HttpClientService httpClientService,
                                     @Value("${agent.intent.enabled:true}") boolean enabled) {
        this.httpClientService = httpClientService;
        this.enabled = enabled;
    }

    @Override
    public IntentResult classify(String message) {
        if (!enabled) {
            // 禁用时返回 null target，由 AgentLoopImpl 回退到 resolveStockFromMessage
            return new IntentResult(IntentType.GENERAL_CHAT, 0, null, null);
        }

        if (message == null || message.isBlank()) {
            return new IntentResult(IntentType.GENERAL_CHAT, 1.0, null,
                    IntentToolGroupMap.getTools(IntentType.GENERAL_CHAT));
        }

        String trimmed = message.trim();

        // === 第一优先级：高特异性意图 ===

        if (isHoldingsQuery(trimmed)) {
            log.info("[IntentClassifier] HOLDINGS_QUERY: {}", trimmed);
            return new IntentResult(IntentType.HOLDINGS_QUERY, 0.95, null,
                    IntentToolGroupMap.getTools(IntentType.HOLDINGS_QUERY));
        }

        if (isGeneralChat(trimmed)) {
            log.info("[IntentClassifier] GENERAL_CHAT: {}", trimmed);
            return new IntentResult(IntentType.GENERAL_CHAT, 0.9, null,
                    IntentToolGroupMap.getTools(IntentType.GENERAL_CHAT));
        }

        if (isDeepAnalysisRequest(trimmed)) {
            IntentResult result = tryResolveStock(trimmed, IntentType.DEEP_ANALYSIS, 0.95);
            log.info("[IntentClassifier] DEEP_ANALYSIS: target={}", result.target());
            return result;
        }

        // === 第二优先级：金融专业意图 ===

        if (isSectorAnalysis(trimmed)) {
            log.info("[IntentClassifier] SECTOR_ANALYSIS: {}", trimmed);
            return new IntentResult(IntentType.SECTOR_ANALYSIS, 0.9, null,
                    IntentToolGroupMap.getTools(IntentType.SECTOR_ANALYSIS));
        }

        if (isMarketNews(trimmed)) {
            log.info("[IntentClassifier] MARKET_NEWS: {}", trimmed);
            return new IntentResult(IntentType.MARKET_NEWS, 0.85, null,
                    IntentToolGroupMap.getTools(IntentType.MARKET_NEWS));
        }

        if (isTradingSentiment(trimmed)) {
            log.info("[IntentClassifier] TRADING_SENTIMENT: {}", trimmed);
            return new IntentResult(IntentType.TRADING_SENTIMENT, 0.9, null,
                    IntentToolGroupMap.getTools(IntentType.TRADING_SENTIMENT));
        }

        if (isFinancialCalc(trimmed)) {
            log.info("[IntentClassifier] FINANCIAL_CALC: {}", trimmed);
            return new IntentResult(IntentType.FINANCIAL_CALC, 0.9, null,
                    IntentToolGroupMap.getTools(IntentType.FINANCIAL_CALC));
        }

        if (isDbQuery(trimmed)) {
            log.info("[IntentClassifier] DB_QUERY: {}", trimmed);
            return new IntentResult(IntentType.DB_QUERY, 0.9, null,
                    IntentToolGroupMap.getTools(IntentType.DB_QUERY));
        }

        // === 第三优先级：个股分析（需要标的解析，成本最高） ===

        if (hasAnalysisIntent(trimmed)) {
            IntentResult result = tryResolveStock(trimmed, IntentType.STOCK_ANALYSIS, 0.85);
            log.info("[IntentClassifier] STOCK_ANALYSIS: target={}", result.target());
            return result;
        }

        // === 兜底 ===
        log.info("[IntentClassifier] GENERAL_CHAT (fallback): {}", trimmed);
        return new IntentResult(IntentType.GENERAL_CHAT, 0.5, null,
                IntentToolGroupMap.getTools(IntentType.GENERAL_CHAT));
    }

    /**
     * 尝试解析标的，成功则返回对应意图，失败则降级为 GENERAL_CHAT
     */
    private IntentResult tryResolveStock(String msg, IntentType intentType, double confidence) {
        // 先尝试提取6位数字代码
        Matcher m = CODE_PATTERN.matcher(msg);
        if (m.find()) {
            String code = m.group(1);
            String name = null;
            try {
                var resolved = StockResolver.resolve(code, httpClientService);
                name = resolved.name();
            } catch (Exception e) {
                log.debug("[IntentClassifier] 代码反查名称失败: {}", e.getMessage());
            }
            var target = new IntentResult.ResolvedTarget(code, name);
            return new IntentResult(intentType, confidence, target,
                    IntentToolGroupMap.getTools(intentType));
        }

        // 剥离分析关键词后尝试名称解析
        String cleaned = stripAnalysisKeywords(msg);
        if (cleaned.isEmpty()) {
            return new IntentResult(IntentType.GENERAL_CHAT, 0.6, null,
                    IntentToolGroupMap.getTools(IntentType.GENERAL_CHAT));
        }

        try {
            var resolved = StockResolver.resolve(cleaned, httpClientService);
            var target = new IntentResult.ResolvedTarget(resolved.code(), resolved.name());
            return new IntentResult(intentType, confidence, target,
                    IntentToolGroupMap.getTools(intentType));
        } catch (IllegalArgumentException e) {
            log.warn("[IntentClassifier] 标的解析失败: {}", e.getMessage());
            // 解析失败时保留原始意图类型（如 DEEP_ANALYSIS），但 target 为 null
            // 仅当原始意图是 STOCK_ANALYSIS 时才降级
            if (intentType == IntentType.STOCK_ANALYSIS) {
                if (isMarketNews(msg)) {
                    return new IntentResult(IntentType.MARKET_NEWS, 0.6, null,
                            IntentToolGroupMap.getTools(IntentType.MARKET_NEWS));
                }
                return new IntentResult(IntentType.GENERAL_CHAT, 0.5, null,
                        IntentToolGroupMap.getTools(IntentType.GENERAL_CHAT));
            }
            // DEEP_ANALYSIS 等其他意图，保留原意图但无标的
            return new IntentResult(intentType, confidence * 0.6, null,
                    IntentToolGroupMap.getTools(intentType));
        }
    }

    // ===== 判断方法 =====

    private boolean isHoldingsQuery(String msg) {
        for (String kw : HOLDINGS_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isGeneralChat(String msg) {
        String lower = msg.toLowerCase();
        // 纯问候（精确匹配）
        for (String g : GREETINGS) {
            if (lower.equals(g) || lower.equals(g + "！") || lower.equals(g + "!")) return true;
        }
        // 概念解释类（排除包含金融计算关键词的情况，如"复利计算"应走 FINANCIAL_CALC）
        boolean hasCalcIntent = isFinancialCalc(msg);
        if (!hasCalcIntent) {
            for (String c : CONCEPT_KEYWORDS) {
                if (msg.contains(c)) return true;
            }
        }
        // 学习类（不含分析意图时）
        for (String l : LEARNING_KEYWORDS) {
            if (msg.contains(l) && !hasAnalysisIntent(msg)) return true;
        }
        return false;
    }

    private boolean isDeepAnalysisRequest(String msg) {
        for (String kw : DEEP_ANALYSIS_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isSectorAnalysis(String msg) {
        for (String kw : SECTOR_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isMarketNews(String msg) {
        boolean hasNews = false;
        for (String kw : NEWS_KEYWORDS) {
            if (msg.contains(kw)) {
                hasNews = true;
                break;
            }
        }
        if (!hasNews) return false;
        // 如果同时包含个股分析关键词和标的代码，可能是个股新闻分析，走 STOCK_ANALYSIS
        if (hasAnalysisIntent(msg) && CODE_PATTERN.matcher(msg).find()) return false;
        return true;
    }

    private boolean isTradingSentiment(String msg) {
        for (String kw : SENTIMENT_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isFinancialCalc(String msg) {
        for (String kw : CALC_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isDbQuery(String msg) {
        for (String kw : DB_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean hasAnalysisIntent(String msg) {
        for (String kw : ANALYSIS_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private String stripAnalysisKeywords(String msg) {
        String cleaned = msg;
        for (String kw : STRIP_KEYWORDS) {
            cleaned = cleaned.replace(kw, "");
        }
        cleaned = cleaned.replaceAll("[，。？！、\\s]+", "").trim();
        return cleaned;
    }

    // ===== 关键词常量 =====

    private static final String[] HOLDINGS_KEYWORDS = {
            "我的基金", "我的持仓", "看看我的基金", "我的仓位",
            "养基宝", "我的投资组合", "账户余额"
    };

    private static final String[] GREETINGS = {
            "你好", "您好", "hi", "hello", "嗨", "你是谁", "介绍一下你自己"
    };

    private static final String[] CONCEPT_KEYWORDS = {
            "什么是", "是什么意思", "怎么理解", "解释一下", "介绍一下概念",
            "复利", "市盈率", "PE", "PB", "ROE", "夏普比率", "什么是ETF",
            "什么是基金", "什么是股票", "什么是债券", "什么是期权"
    };

    private static final String[] LEARNING_KEYWORDS = {
            "学习", "教程", "入门", "基础知识", "怎么学", "如何入门"
    };

    private static final String[] DEEP_ANALYSIS_KEYWORDS = {
            "深度分析", "深入分析", "全面分析", "详细分析", "深度研究",
            "深度调研", "深度剖析", "深入研究", "全面研究", "详细研究",
            "个股分析", "个股研究"
    };

    private static final String[] SECTOR_KEYWORDS = {
            "板块", "行业", "概念", "赛道", "题材", "领域"
    };

    private static final String[] NEWS_KEYWORDS = {
            "今日新闻", "最新消息", "财经新闻", "市场新闻", "热点新闻",
            "新闻", "资讯", "快讯", "突发事件", "政策", "央行", "美联储",
            "利率", "降息", "加息", "IPO", "注册制", "退市"
    };

    private static final String[] SENTIMENT_KEYWORDS = {
            "涨停", "跌停", "炸板", "连板", "打板", "情绪", "龙虎榜",
            "涨停池", "跌停池", "封板", "炸板率", "涨停揭秘",
            "热榜", "人气榜", "热度", "热门股票", "热搜",
            "市场情绪", "情绪面"
    };

    private static final String[] CALC_KEYWORDS = {
            "计算", "算一下", "帮我算", "多少收益", "收益率", "月供",
            "贷款", "NPV", "IRR", "年化", "复利计算"
    };

    private static final String[] DB_KEYWORDS = {
            "查询数据库", "SQL", "数据库", "执行查询"
    };

    private static final String[] ANALYSIS_KEYWORDS = {
            "分析", "研究", "调研", "估值", "行情", "股价", "怎么样",
            "如何", "值得入手吗", "值得买吗", "可以买吗", "能买吗",
            "值得投资吗", "值得持有吗", "现在能买吗", "目前怎么样",
            "看好", "有前途吗", "前景如何", "还能涨吗", "还能买吗",
            "研报", "现在", "目前",
            "现价", "最新价", "报价", "多少钱", "价格", "涨跌",
            "涨幅", "跌幅", "市值", "PE", "PB", "换手率", "成交量",
            "K线", "均线", "分时",
            "期权", "融资融券", "大宗交易", "北向资金", "解禁",
            "分红", "股东户数", "利润表", "资产负债表", "现金流量表",
            "财报", "互动易", "概念热度"
    };

    private static final String[] STRIP_KEYWORDS = {
            "深入分析", "深度分析", "全面分析", "详细分析", "深度研究",
            "深度调研", "深度剖析", "深入研究", "全面研究", "详细研究",
            "个股分析", "个股研究", "帮我分析", "帮我看看", "帮我研究",
            "分析一下", "研究一下", "分析", "研究", "调研", "估值",
            "行情", "股价", "怎么样", "如何", "值得入手吗", "值得买吗",
            "可以买吗", "可以入手吗", "能买吗", "值得投资吗", "值得持有吗",
            "现在能买吗", "现在可以买吗", "目前怎么样", "可以买", "看好",
            "看好吗", "有前途吗", "前景如何", "还能涨吗", "还能买吗",
            "研报", "现在", "目前",
            "现价", "最新价", "报价", "多少钱", "价格", "涨跌",
            "涨幅", "跌幅", "市值", "换手率", "成交量",
            "K线", "均线", "分时",
            "期权", "融资融券", "大宗交易", "北向资金", "解禁",
            "分红", "股东户数", "利润表", "资产负债表", "现金流量表",
            "财报", "互动易", "概念热度"
    };
}
