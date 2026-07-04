package com.itlk.myclaudecode.tool.astock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockSignalRouterTool A股信号工具测试")
class AStockSignalRouterToolTest {

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    private AStockSignalRouterTool tool;

    // 模拟板块归属API返回
    private static final String CONCEPT_BLOCKS_RESPONSE = """
            {"result":{"data":[{"f12":"BK0477","f14":"白酒"},{"f12":"BK0437","f14":"贵州板块"},{"f12":"BK0733","f14":"沪股通"}]}}""";

    // 模拟龙虎榜API返回
    private static final String DRAGON_TIGER_RESPONSE = """
            {"result":{"data":[{"SECURITY_CODE":"600519","SECURITY_NAME_ABBR":"贵州茅台","TRADE_DATE":"2025-01-02","BUYER_NAME":"机构专用","SELLER_NAME":"中信证券上海分公司","NET_BUY_AMT":5000.00}]}}""";

    // 模拟行业排名API返回
    private static final String INDUSTRY_RANKING_RESPONSE = """
            {"result":{"data":[{"f14":"白酒","f3":2.50,"f104":50000,"f105":30000,"f128":"贵州茅台","f140":1800.00}]}}""";

    @BeforeEach
    void setUp() {
        tool = new AStockSignalRouterTool(emRateLimiter);
    }

    @Nested
    @DisplayName("a_stock_signal 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_signal("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_signal(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_signal("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("conceptBlocks 板块归属")
    class ConceptBlocksTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_signal("conceptBlocks", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析板块数据")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(CONCEPT_BLOCKS_RESPONSE);
            String result = tool.a_stock_signal("conceptBlocks", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("白酒") || result.contains("板块") || result.contains("概念"),
                    "应包含板块信息");
        }
    }

    @Nested
    @DisplayName("fundFlowMinute 分钟资金流")
    class FundFlowMinuteTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_signal("fundFlowMinute", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":{\"klines\":[\"09:30,1000,500,300,200\"]}}}");
            String result = tool.a_stock_signal("fundFlowMinute", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }
    }

    @Nested
    @DisplayName("dragonTigerBoard 个股龙虎榜")
    class DragonTigerBoardTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_signal("dragonTigerBoard", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析龙虎榜数据")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(DRAGON_TIGER_RESPONSE);
            String result = tool.a_stock_signal("dragonTigerBoard",
                    "{\"stockCode\":\"600519\",\"tradeDate\":\"2025-01-02\"}");
            assertTrue(result.contains("龙虎榜") || result.contains("贵州茅台") || result.contains("席位"),
                    "应包含龙虎榜信息");
        }

        @Test
        @DisplayName("API返回空数据 → 返回提示")
        void emptyData() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[]}}");
            String result = tool.a_stock_signal("dragonTigerBoard",
                    "{\"stockCode\":\"600519\",\"lookBackDays\":30}");
            assertTrue(result.contains("未找到") || result.contains("没有") || result.contains("0") || result.contains("龙虎榜"),
                    "空数据应返回提示");
        }
    }

    @Nested
    @DisplayName("dailyDragonTiger 全市场龙虎榜")
    class DailyDragonTigerTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(DRAGON_TIGER_RESPONSE);
            String result = tool.a_stock_signal("dailyDragonTiger", "{}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("指定日期")
        void withDate() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(DRAGON_TIGER_RESPONSE);
            String result = tool.a_stock_signal("dailyDragonTiger",
                    "{\"tradeDate\":\"2025-01-02\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("lockupExpiry 限售解禁")
    class LockupExpiryTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_signal("lockupExpiry", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[{\"SECURITY_CODE\":\"600519\",\"FREE_DATE\":\"2025-06-01\",\"FREE_SHARES\":1000.00}]}}");
            String result = tool.a_stock_signal("lockupExpiry", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }
    }

    @Nested
    @DisplayName("industryRanking 行业排名")
    class IndustryRankingTest {

        @Test
        @DisplayName("默认参数查询 → 解析行业排名")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(INDUSTRY_RANKING_RESPONSE);
            String result = tool.a_stock_signal("industryRanking", "{}");
            assertTrue(result.contains("行业") || result.contains("排名") || result.contains("板块"),
                    "应包含行业排名信息");
        }

        @Test
        @DisplayName("自定义topN")
        void withTopN() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(INDUSTRY_RANKING_RESPONSE);
            String result = tool.a_stock_signal("industryRanking", "{\"topN\":10}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_signal("conceptBlocks", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }

        @Test
        @DisplayName("空params → 正常处理")
        void emptyParams() {
            String result = tool.a_stock_signal("conceptBlocks", "");
            assertNotNull(result, "空params不应崩溃");
        }

        @Test
        @DisplayName("null params → 正常处理")
        void nullParams() {
            String result = tool.a_stock_signal("conceptBlocks", null);
            assertNotNull(result, "null params不应崩溃");
        }
    }
}
