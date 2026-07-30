package com.xiaomo.agent.agent.intent;

import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.workflow.util.StockResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则优先的意图分类器。
 * <p>
 * 分类策略：
 * 1. 提取分析深度（DEEP 关键词），有深度标记时先剥离再做业务分类
 * 2. 优先匹配高特异性意图（持仓、通用对话）
 * 3. 再匹配金融专业意图（新闻、板块、打板、计算、DB）
 * 4. 最后匹配个股分析（需要标的解析，成本最高）
 * 5. 兜底为 GENERAL_CHAT
 * <p>
 * 修复记录：
 * - Bug#1: GENERAL_CHAT 吞掉指标查询 — 从 CONCEPT_KEYWORDS 移除 PE/PB/ROE 等指标词
 * - Bug#2: NOISE_PREFIX_PATTERN 字符集合误用 — 由 StockResolver 内部处理
 * - Bug#3: 时间词全局删除破坏股票名称 — tryResolveStock 改为先尝试原文再剥离
 * - 重构: DEEP_ANALYSIS 从独立意图改为 AnalysisDepth 标记
 */
@Component
@Slf4j
public class RuleBasedIntentClassifier implements IntentClassifier {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    /** 匹配"深度分析"、"详细研究"等深度标记词 */
    private static final Pattern DEPTH_PATTERN = Pattern.compile(
            "深入分析|深度分析|全面分析|详细分析|深度研究|深度调研|深度剖析|深入研究|全面研究|详细研究|个股分析|个股研究"
    );

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
            return new IntentResult(IntentType.GENERAL_CHAT, AnalysisDepth.NORMAL, 0, null,
                    ToolPolicy.plannerManaged());
        }

        if (message == null || message.isBlank()) {
            return new IntentResult(IntentType.GENERAL_CHAT, AnalysisDepth.NORMAL, 1.0, null,
                    IntentToolGroupMap.getPolicy(IntentType.GENERAL_CHAT, AnalysisDepth.NORMAL));
        }

        String trimmed = message.trim();

        // Step 1: 提取分析深度（只标记，不剥离文本，保留"分析"关键词供后续分类使用）
        AnalysisDepth depth = AnalysisDepth.NORMAL;
        Matcher depthMatcher = DEPTH_PATTERN.matcher(trimmed);
        if (depthMatcher.find()) {
            depth = AnalysisDepth.DEEP;
            log.info("[IntentClassifier] 检测到深度分析标记");
        }

        // Step 2: 业务意图分类
        IntentResult result = classifyBusinessIntent(trimmed, depth);

        // Step 3: 如果有深度标记但业务分类不是需要标的的意图，强制设为 PLANNER_MANAGED
        if (depth == AnalysisDepth.DEEP && result.policy().mode() != ToolPolicyMode.PLANNER_MANAGED) {
            result = new IntentResult(result.intent(), AnalysisDepth.DEEP, result.confidence(),
                    result.target(), ToolPolicy.plannerManaged());
        }

        return result;
    }

    /**
     * 纯业务意图分类（不含深度判断）
     */
    private IntentResult classifyBusinessIntent(String trimmed, AnalysisDepth depth) {
        // === 第一优先级：高特异性意图 ===

        if (isHoldingsQuery(trimmed)) {
            log.info("[IntentClassifier] HOLDINGS_QUERY: {}", trimmed);
            return new IntentResult(IntentType.HOLDINGS_QUERY, depth, 0.95, null,
                    IntentToolGroupMap.getPolicy(IntentType.HOLDINGS_QUERY, depth));
        }

        if (isGeneralChat(trimmed)) {
            log.info("[IntentClassifier] GENERAL_CHAT: {}", trimmed);
            return new IntentResult(IntentType.GENERAL_CHAT, depth, 0.9, null,
                    IntentToolGroupMap.getPolicy(IntentType.GENERAL_CHAT, depth));
        }

        // === 第二优先级：金融专业意图 ===

        if (isSectorAnalysis(trimmed)) {
            log.info("[IntentClassifier] SECTOR_ANALYSIS: {}", trimmed);
            return new IntentResult(IntentType.SECTOR_ANALYSIS, depth, 0.9, null,
                    IntentToolGroupMap.getPolicy(IntentType.SECTOR_ANALYSIS, depth));
        }

        if (isFinancialCalc(trimmed)) {
            log.info("[IntentClassifier] FINANCIAL_CALC: {}", trimmed);
            return new IntentResult(IntentType.FINANCIAL_CALC, depth, 0.9, null,
                    IntentToolGroupMap.getPolicy(IntentType.FINANCIAL_CALC, depth));
        }

        if (isMarketNews(trimmed)) {
            log.info("[IntentClassifier] MARKET_NEWS: {}", trimmed);
            return new IntentResult(IntentType.MARKET_NEWS, depth, 0.85, null,
                    IntentToolGroupMap.getPolicy(IntentType.MARKET_NEWS, depth));
        }

        if (isTradingSentiment(trimmed)) {
            log.info("[IntentClassifier] TRADING_SENTIMENT: {}", trimmed);
            return new IntentResult(IntentType.TRADING_SENTIMENT, depth, 0.9, null,
                    IntentToolGroupMap.getPolicy(IntentType.TRADING_SENTIMENT, depth));
        }

        if (isDbQuery(trimmed)) {
            log.info("[IntentClassifier] DB_QUERY: {}", trimmed);
            return new IntentResult(IntentType.DB_QUERY, depth, 0.9, null,
                    IntentToolGroupMap.getPolicy(IntentType.DB_QUERY, depth));
        }

        // === 第三优先级：个股分析（需要标的解析，成本最高） ===

        if (hasAnalysisIntent(trimmed)) {
            IntentResult result = tryResolveStock(trimmed, depth);
            log.info("[IntentClassifier] STOCK_ANALYSIS: target={}", result.target());
            return result;
        }

        // === 兜底 ===
        log.info("[IntentClassifier] GENERAL_CHAT (fallback): {}", trimmed);
        return new IntentResult(IntentType.GENERAL_CHAT, depth, 0.5, null,
                IntentToolGroupMap.getPolicy(IntentType.GENERAL_CHAT, depth));
    }

    /**
     * 尝试解析标的，成功则返回 STOCK_ANALYSIS，失败则降级
     * <p>
     * Bug#3 修复：先用原文尝试解析（保留"今天国际"等股票名称），
     * 失败后再剥离时间词重试
     */
    private IntentResult tryResolveStock(String msg, AnalysisDepth depth) {
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
            return new IntentResult(IntentType.STOCK_ANALYSIS, depth, 0.85, target,
                    IntentToolGroupMap.getPolicy(IntentType.STOCK_ANALYSIS, depth));
        }

        // Bug#3 修复：先用原文尝试解析，保留"今天国际"等包含时间词的股票名称
        String cleaned = stripAnalysisKeywords(msg, false);
        if (!cleaned.isEmpty()) {
            try {
                var resolved = StockResolver.resolve(cleaned, httpClientService);
                var target = new IntentResult.ResolvedTarget(resolved.code(), resolved.name());
                return new IntentResult(IntentType.STOCK_ANALYSIS, depth, 0.85, target,
                        IntentToolGroupMap.getPolicy(IntentType.STOCK_ANALYSIS, depth));
            } catch (IllegalArgumentException e) {
                log.debug("[IntentClassifier] 原文解析失败，尝试剥离时间词: {}", e.getMessage());
            }
        }

        // 原文解析失败，剥离时间词后重试（处理"今天行情怎么样"→ 降级 的场景）
        String cleanedWithTimeStripped = stripAnalysisKeywords(msg, true);
        if (!cleanedWithTimeStripped.isEmpty() && !cleanedWithTimeStripped.equals(cleaned)) {
            try {
                var resolved = StockResolver.resolve(cleanedWithTimeStripped, httpClientService);
                var target = new IntentResult.ResolvedTarget(resolved.code(), resolved.name());
                return new IntentResult(IntentType.STOCK_ANALYSIS, depth, 0.85, target,
                        IntentToolGroupMap.getPolicy(IntentType.STOCK_ANALYSIS, depth));
            } catch (IllegalArgumentException e) {
                log.warn("[IntentClassifier] 标的解析失败: {}", e.getMessage());
            }
        }

        // 两次都失败，降级
        if (isMarketNews(msg)) {
            return new IntentResult(IntentType.MARKET_NEWS, depth, 0.6, null,
                    IntentToolGroupMap.getPolicy(IntentType.MARKET_NEWS, depth));
        }
        return new IntentResult(IntentType.GENERAL_CHAT, depth, 0.5, null,
                IntentToolGroupMap.getPolicy(IntentType.GENERAL_CHAT, depth));
    }

    // ===== 判断方法 =====

    private boolean isHoldingsQuery(String msg) {
        for (String kw : HOLDINGS_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 通用对话判断。
     * <p>
     * Bug#1 修复：概念解释类增加排除逻辑 —— 如果消息同时包含分析意图关键词，
     * 则不判定为通用对话（如"茅台PE是多少"应走个股分析）。
     * 但明确的解释句式（"什么是X"、"X是什么意思"）不受此限制。
     */
    private boolean isGeneralChat(String msg) {
        String lower = msg.toLowerCase();
        // 纯问候（精确匹配）
        for (String g : GREETINGS) {
            if (lower.equals(g) || lower.equals(g + "！") || lower.equals(g + "!")) return true;
        }
        // 明确的解释句式（不受分析意图排除）
        for (String p : EXPLANATION_PATTERNS) {
            if (msg.contains(p)) return true;
        }
        // 概念类关键词（排除包含金融计算关键词的情况）
        boolean hasCalcIntent = isFinancialCalc(msg);
        if (!hasCalcIntent) {
            for (String c : CONCEPT_KEYWORDS) {
                if (msg.contains(c) && !hasAnalysisIntent(msg)) return true;
            }
        }
        // 学习类（不含分析意图时）
        for (String l : LEARNING_KEYWORDS) {
            if (msg.contains(l) && !hasAnalysisIntent(msg)) return true;
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

    /**
     * 剥离分析关键词，提取可能的股票名称。
     *
     * @param msg             原始消息
     * @param stripTimeWords  是否剥离时间词（首次尝试 false，保留"今天国际"等股票名称）
     */
    private String stripAnalysisKeywords(String msg, boolean stripTimeWords) {
        String cleaned = msg;
        for (String kw : STRIP_KEYWORDS) {
            cleaned = cleaned.replace(kw, "");
        }
        if (stripTimeWords) {
            for (String kw : TIME_KEYWORDS) {
                cleaned = cleaned.replace(kw, "");
            }
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

    /** 明确的解释句式 —— 即使包含指标词也不走分析 */
    private static final String[] EXPLANATION_PATTERNS = {
            "什么是", "是什么意思", "怎么理解", "解释一下", "介绍一下概念",
            "什么是ETF", "什么是基金", "什么是股票", "什么是债券", "什么是期权"
    };

    /** 概念类关键词 —— 已移除 PE/PB/ROE 等指标词（Bug#1 修复） */
    private static final String[] CONCEPT_KEYWORDS = {
            "复利", "夏普比率"
    };

    private static final String[] LEARNING_KEYWORDS = {
            "学习", "教程", "入门", "基础知识", "怎么学", "如何入门"
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
            "财报", "互动易", "概念热度",
            "角度", "维度", "方面", "基本面", "资金面", "技术面", "情绪面",
            "市盈率", "市净率", "ROE", "EPS"
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
            "财报", "互动易", "概念热度",
            "角度", "维度", "方面", "基本面", "资金面", "技术面", "情绪面",
            "两个", "三个", "多个", "几个", "全面", "综合",
            "市盈率", "市净率", "ROE", "EPS"
    };

    private static final String[] TIME_KEYWORDS = {
            "今天", "今日", "明天", "明日", "昨天", "昨日",
            "上周", "本周", "下周", "上个月", "这个月", "下个月",
            "最近", "近期", "前段时间", "这段时间"
    };
}
