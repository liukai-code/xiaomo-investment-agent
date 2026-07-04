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
@DisplayName("AStockLimitUpRouterTool A股涨跌停工具测试")
class AStockLimitUpRouterToolTest {

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    private AStockLimitUpRouterTool tool;

    private static final String ZT_POOL_RESPONSE = """
            {"result":{"data":{"pool":[{"c":"600519","n":"贵州茅台","zdp":10.00,"lbc":3,"fund":50000.00}]}}}""";

    private static final String ZB_POOL_RESPONSE = """
            {"result":{"data":{"pool":[{"c":"600519","n":"贵州茅台","zdp":9.50,"fund":30000.00}]}}}""";

    private static final String DT_POOL_RESPONSE = """
            {"result":{"data":{"pool":[{"c":"000001","n":"某股票","zdp":-10.00}]}}}""";

    private static final String SENTIMENT_RESPONSE = """
            {"result":{"data":{"ztCount":50,"dtCount":10,"zbCount":5,"zbRate":9.10}}}""";

    @BeforeEach
    void setUp() {
        tool = new AStockLimitUpRouterTool(emRateLimiter);
    }

    @Nested
    @DisplayName("a_stock_limit_up 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_limit_up("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_limit_up(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_limit_up("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("ztPool 涨停池")
    class ZtPoolTest {

        @Test
        @DisplayName("默认参数查询 → 解析涨停数据")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(ZT_POOL_RESPONSE);
            String result = tool.a_stock_limit_up("ztPool", "{}");
            assertTrue(result.contains("涨停") || result.contains("600519") || result.contains("贵州茅台"),
                    "应包含涨停信息");
        }

        @Test
        @DisplayName("指定日期查询")
        void withDate() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(ZT_POOL_RESPONSE);
            String result = tool.a_stock_limit_up("ztPool", "{\"date\":\"20250102\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("API返回空数据 → 返回提示")
        void emptyData() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":{\"pool\":[]}}}");
            String result = tool.a_stock_limit_up("ztPool", "{}");
            assertTrue(result.contains("未找到") || result.contains("没有") || result.contains("0") || result.contains("涨停"),
                    "空数据应返回提示");
        }
    }

    @Nested
    @DisplayName("zbPool 炸板池")
    class ZbPoolTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(ZB_POOL_RESPONSE);
            String result = tool.a_stock_limit_up("zbPool", "{}");
            assertNotNull(result, "不应返回null");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }
    }

    @Nested
    @DisplayName("dtPool 跌停池")
    class DtPoolTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(DT_POOL_RESPONSE);
            String result = tool.a_stock_limit_up("dtPool", "{}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("yztPool 预涨停池")
    class YztPoolTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":{\"pool\":[]}}}");
            String result = tool.a_stock_limit_up("yztPool", "{}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("thsLimitUpPool 同花顺涨停池")
    class ThsLimitUpPoolTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":{\"pool\":[]}}}");
            String result = tool.a_stock_limit_up("thsLimitUpPool", "{}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("sentimentOverview 情绪总览")
    class SentimentOverviewTest {

        @Test
        @DisplayName("默认参数查询 → 解析情绪数据")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(SENTIMENT_RESPONSE);
            String result = tool.a_stock_limit_up("sentimentOverview", "{}");
            assertTrue(result.contains("涨停") || result.contains("跌停") || result.contains("炸板") || result.contains("情绪"),
                    "应包含情绪指标");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_limit_up("ztPool", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }
    }
}
