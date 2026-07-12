package com.itlk.myclaudecode.agent.intent;

import com.itlk.myclaudecode.common.config.HttpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuleBasedIntentClassifierTest {

    private HttpClientService httpClientService;
    private RuleBasedIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        httpClientService = mock(HttpClientService.class);
        classifier = new RuleBasedIntentClassifier(httpClientService, true);
    }

    @Nested
    @DisplayName("通用对话")
    class GeneralChat {

        @ParameterizedTest
        @ValueSource(strings = {"你好", "您好", "hi", "hello", "嗨"})
        void 纯问候应分类为GENERAL_CHAT(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.GENERAL_CHAT, result.intent());
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"什么是ETF", "什么是基金", "市盈率是什么意思", "解释一下复利"})
        void 概念解释应分类为GENERAL_CHAT(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.GENERAL_CHAT, result.intent());
            assertTrue(result.suggestedTools().isEmpty());
        }

        @Test
        void 学习类应分类为GENERAL_CHAT() {
            IntentResult result = classifier.classify("股票入门教程");
            assertEquals(IntentType.GENERAL_CHAT, result.intent());
        }

        @Test
        void 空消息应分类为GENERAL_CHAT() {
            IntentResult result = classifier.classify("");
            assertEquals(IntentType.GENERAL_CHAT, result.intent());
        }

        @Test
        void null消息应分类为GENERAL_CHAT() {
            IntentResult result = classifier.classify(null);
            assertEquals(IntentType.GENERAL_CHAT, result.intent());
        }
    }

    @Nested
    @DisplayName("市场新闻")
    class MarketNews {

        @ParameterizedTest
        @ValueSource(strings = {
                "查询今日新闻，并总结一下对周一开盘的影响",
                "今日新闻",
                "最新消息",
                "财经新闻",
                "市场新闻",
                "热点新闻",
                "最近有什么资讯",
                "央行降息对市场有什么影响"
        })
        void 新闻类查询应分类为MARKET_NEWS(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.MARKET_NEWS, result.intent(), "消息: " + msg);
            // MARKET_NEWS 不应包含 a_stock_quote，防止漂移到个股分析
            assertNotNull(result.suggestedTools());
            assertFalse(result.suggestedTools().contains("a_stock_quote"),
                    "MARKET_NEWS 不应包含 a_stock_quote 工具");
            // 应包含新闻工具
            assertTrue(result.suggestedTools().contains("a_stock_news"),
                    "MARKET_NEWS 应包含 a_stock_news 工具");
        }

        @Test
        void 带有分析关键词但无标的的新闻查询应分类为MARKET_NEWS() {
            // "分析一下今天的新闻" - 有"分析"关键词但无股票名称
            IntentResult result = classifier.classify("分析一下今天的新闻");
            assertEquals(IntentType.MARKET_NEWS, result.intent());
        }
    }

    @Nested
    @DisplayName("板块分析")
    class SectorAnalysis {

        @ParameterizedTest
        @ValueSource(strings = {
                "半导体板块怎么样",
                "新能源行业分析",
                "人工智能概念",
                "芯片赛道前景",
                "光伏题材分析"
        })
        void 板块行业查询应分类为SECTOR_ANALYSIS(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.SECTOR_ANALYSIS, result.intent(), "消息: " + msg);
            assertNull(result.target(), "板块分析不应有标的锁定");
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("a_stock_signal"));
        }
    }

    @Nested
    @DisplayName("打板情绪")
    class TradingSentiment {

        @ParameterizedTest
        @ValueSource(strings = {
                "今天涨停的股票",
                "跌停池",
                "炸板率多少",
                "连板梯队",
                "龙虎榜",
                "涨停揭秘",
                "查一下热榜",
                "热榜",
                "人气榜",
                "热搜",
                "市场情绪怎么样",
                "情绪面分析"
        })
        void 打板情绪查询应分类为TRADING_SENTIMENT(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.TRADING_SENTIMENT, result.intent(), "消息: " + msg);
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("a_stock_limit_up"));
            assertTrue(result.suggestedTools().contains("a_stock_sentiment"));
        }
    }

    @Nested
    @DisplayName("持仓查询")
    class HoldingsQuery {

        @ParameterizedTest
        @ValueSource(strings = {
                "我的基金",
                "我的持仓",
                "看看我的基金",
                "我的仓位",
                "养基宝"
        })
        void 持仓查询应分类为HOLDINGS_QUERY(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.HOLDINGS_QUERY, result.intent(), "消息: " + msg);
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("getMyHoldings"));
        }
    }

    @Nested
    @DisplayName("金融计算")
    class FinancialCalc {

        @ParameterizedTest
        @ValueSource(strings = {
                "帮我算一下收益率",
                "复利计算",
                "贷款月供多少",
                "NPV计算"
        })
        void 金融计算应分类为FINANCIAL_CALC(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.FINANCIAL_CALC, result.intent(), "消息: " + msg);
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("financial_calculator"));
        }
    }

    @Nested
    @DisplayName("数据库查询")
    class DbQuery {

        @ParameterizedTest
        @ValueSource(strings = {
                "查询数据库",
                "执行SQL查询",
                "数据库里有什么表"
        })
        void 数据库查询应分类为DB_QUERY(String msg) {
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.DB_QUERY, result.intent(), "消息: " + msg);
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("getDatabaseSchema"));
            assertTrue(result.suggestedTools().contains("executeQuery"));
        }
    }

    @Nested
    @DisplayName("深度分析")
    class DeepAnalysis {

        @Test
        void 深度分析应分类为DEEP_ANALYSIS() {
            // 注意：StockResolver 需要 HTTP 调用，此处无 mock 所以 target 可能为 null
            // 但 intent 应始终为 DEEP_ANALYSIS
            IntentResult result = classifier.classify("深度分析宁德时代");
            assertEquals(IntentType.DEEP_ANALYSIS, result.intent());
            assertNull(result.suggestedTools(), "DEEP_ANALYSIS 的 suggestedTools 应为 null");
        }
    }

    @Nested
    @DisplayName("个股分析")
    class StockAnalysis {

        @Test
        void 带数字代码的分析应分类为STOCK_ANALYSIS() throws Exception {
            // Mock StockResolver 返回
            when(httpClientService.get(anyString(), any())).thenReturn(
                    "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"MktNum\":\"1\"}]}}");
            IntentResult result = classifier.classify("分析600519");
            assertEquals(IntentType.STOCK_ANALYSIS, result.intent());
            assertNotNull(result.target());
            assertEquals("600519", result.target().code());
        }

        @Test
        void 带股票名的分析应分类为STOCK_ANALYSIS() throws Exception {
            // Mock StockResolver 返回
            when(httpClientService.get(anyString(), any())).thenReturn(
                    "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"MktNum\":\"1\"}]}}");
            IntentResult result = classifier.classify("分析茅台");
            assertEquals(IntentType.STOCK_ANALYSIS, result.intent());
            assertNotNull(result.target());
        }

        @Test
        void 个股分析应包含全量A股工具() throws Exception {
            when(httpClientService.get(anyString(), any())).thenReturn(
                    "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"MktNum\":\"1\"}]}}");
            IntentResult result = classifier.classify("分析600519");
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("a_stock_quote"));
            assertTrue(result.suggestedTools().contains("a_stock_report"));
            assertTrue(result.suggestedTools().contains("a_stock_news"));
            assertTrue(result.suggestedTools().contains("a_stock_capital"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "600519的期权",
                "茅台的融资融券",
                "茅台大宗交易",
                "茅台北向资金",
                "600519解禁日期",
                "茅台分红历史",
                "茅台股东户数",
                "茅台利润表",
                "茅台资产负债表",
                "茅台现金流量表",
                "茅台财报",
                "茅台互动易"
        })
        void 金融数据查询带标的应分类为STOCK_ANALYSIS(String msg) throws Exception {
            when(httpClientService.get(anyString(), any())).thenReturn(
                    "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"MktNum\":\"1\"}]}}");
            IntentResult result = classifier.classify(msg);
            assertEquals(IntentType.STOCK_ANALYSIS, result.intent(), "消息: " + msg);
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("a_stock_capital"));
            assertTrue(result.suggestedTools().contains("a_stock_news"));
        }

        @Test
        void 概念热度带标的应分类为SECTOR_ANALYSIS() throws Exception {
            // "概念"在SECTOR_KEYWORDS中优先级更高，且SECTOR_TOOLS包含a_stock_sentiment
            when(httpClientService.get(anyString(), any())).thenReturn(
                    "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"MktNum\":\"1\"}]}}");
            IntentResult result = classifier.classify("茅台概念热度");
            assertEquals(IntentType.SECTOR_ANALYSIS, result.intent());
            assertNotNull(result.suggestedTools());
            assertTrue(result.suggestedTools().contains("a_stock_sentiment"));
        }
    }

    @Nested
    @DisplayName("禁用模式")
    class Disabled {

        @Test
        void 禁用时应返回confidence0且suggestedTools为null() {
            RuleBasedIntentClassifier disabledClassifier = new RuleBasedIntentClassifier(httpClientService, false);
            IntentResult result = disabledClassifier.classify("分析茅台");
            assertEquals(0, result.confidence());
            assertNull(result.suggestedTools());
        }
    }

    @Nested
    @DisplayName("意图优先级")
    class Priority {

        @Test
        void 板块关键词应优先于个股分析() {
            // "半导体板块分析" 包含"分析"关键词，但"板块"应优先
            IntentResult result = classifier.classify("半导体板块分析");
            assertEquals(IntentType.SECTOR_ANALYSIS, result.intent());
        }

        @Test
        void 持仓查询应优先于通用对话() {
            IntentResult result = classifier.classify("看看我的基金");
            assertEquals(IntentType.HOLDINGS_QUERY, result.intent());
        }

        @Test
        void 新闻查询不应漂移到个股分析() {
            IntentResult result = classifier.classify("查询今日新闻，并总结一下对周一开盘的影响");
            assertEquals(IntentType.MARKET_NEWS, result.intent());
            // 确保不包含行情工具
            assertNotNull(result.suggestedTools());
            assertFalse(result.suggestedTools().contains("a_stock_quote"));
        }
    }
}
