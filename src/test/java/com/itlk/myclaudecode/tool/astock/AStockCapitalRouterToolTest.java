package com.itlk.myclaudecode.tool.astock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockCapitalRouterTool A股资金工具测试")
class AStockCapitalRouterToolTest {

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    @Mock
    private StringRedisTemplate redisTemplate;

    private AStockCapitalRouterTool tool;

    private static final String MARGIN_TRADING_RESPONSE = """
            {"result":{"data":[{"SECURITY_CODE":"600519","SECURITY_NAME_ABBR":"贵州茅台","TRADE_DATE":"2025-01-02","RZYE":5000000.00,"RQYE":100000.00}]}}""";

    private static final String BLOCK_TRADE_RESPONSE = """
            {"result":{"data":[{"SECURITY_CODE":"600519","SECURITY_NAME_ABBR":"贵州茅台","TRADE_DATE":"2025-01-02","DEAL_PRICE":1800.00,"DEAL_VOLUME":50000}]}}""";

    private static final String HOLDER_NUM_RESPONSE = """
            {"result":{"data":[{"SECURITY_CODE":"600519","SECURITY_NAME_ABBR":"贵州茅台","END_DATE":"2025-01-02","HOLDER_NUM":100000}]}}""";

    private static final String DIVIDEND_RESPONSE = """
            {"result":{"data":[{"SECURITY_CODE":"600519","SECURITY_NAME_ABBR":"贵州茅台","EX_DIVIDEND_DATE":"2025-01-02","PRETAX_BONUS_RMB":10.00}]}}""";

    private static final String FUND_FLOW_RESPONSE = """
            {"result":{"data":{"klines":["2025-01-02,1000,500,300,200,1800.00"]}}}""";

    @BeforeEach
    void setUp() {
        tool = new AStockCapitalRouterTool(emRateLimiter, redisTemplate);
    }

    @Nested
    @DisplayName("a_stock_capital 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_capital("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_capital(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_capital("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("marginTrading 融资融券")
    class MarginTradingTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_capital("marginTrading", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析融资融券数据")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(MARGIN_TRADING_RESPONSE);
            String result = tool.a_stock_capital("marginTrading", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("融资") || result.contains("融券") || result.contains("600519"),
                    "应包含融资融券信息");
        }
    }

    @Nested
    @DisplayName("blockTrade 大宗交易")
    class BlockTradeTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_capital("blockTrade", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(BLOCK_TRADE_RESPONSE);
            String result = tool.a_stock_capital("blockTrade", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }
    }

    @Nested
    @DisplayName("holderNumChange 股东户数")
    class HolderNumChangeTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_capital("holderNumChange", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(HOLDER_NUM_RESPONSE);
            String result = tool.a_stock_capital("holderNumChange", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("dividendHistory 分红历史")
    class DividendHistoryTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_capital("dividendHistory", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(DIVIDEND_RESPONSE);
            String result = tool.a_stock_capital("dividendHistory", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("fundFlow120d 120日资金流")
    class FundFlow120dTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_capital("fundFlow120d", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(FUND_FLOW_RESPONSE);
            String result = tool.a_stock_capital("fundFlow120d", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("northboundFlow 北向资金")
    class NorthboundFlowTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[{\"TRADE_DATE\":\"2025-01-02\",\"NET_BUY_AMT\":5000.00}]}}");
            String result = tool.a_stock_capital("northboundFlow", "{}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_capital("marginTrading", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }
    }
}
