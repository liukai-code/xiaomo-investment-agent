package com.itlk.myclaudecode.tool.astock;

import com.itlk.myclaudecode.common.config.HttpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockQuoteRouterTool A股行情工具测试")
class AStockQuoteRouterToolTest {

    @Mock
    private HttpClientService httpClientService;

    private AStockQuoteRouterTool tool;

    @BeforeEach
    void setUp() {
        tool = new AStockQuoteRouterTool(httpClientService);
    }

    @Nested
    @DisplayName("a_stock_quote 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_quote("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_quote(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_quote("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }

        @Test
        @DisplayName("mootdxKline → 返回未实现提示")
        void mootdxKlineNotImplemented() {
            String result = tool.a_stock_quote("mootdxKline", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("尚未实现") || result.contains("请使用"),
                    "mootdx应返回未实现提示");
        }

        @Test
        @DisplayName("mootdxQuotes → 返回未实现提示")
        void mootdxQuotesNotImplemented() {
            String result = tool.a_stock_quote("mootdxQuotes", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("尚未实现") || result.contains("请使用"),
                    "mootdx应返回未实现提示");
        }

        @Test
        @DisplayName("mootdxTransaction → 返回未实现提示")
        void mootdxTransactionNotImplemented() {
            String result = tool.a_stock_quote("mootdxTransaction", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("尚未实现") || result.contains("请使用"),
                    "mootdx应返回未实现提示");
        }
    }

    @Nested
    @DisplayName("tencentQuote 批量行情查询")
    class TencentQuoteTest {

        @Test
        @DisplayName("单个股票代码")
        void singleStock() {
            // 由于需要实际网络请求，这里只测试参数解析
            String result = tool.a_stock_quote("tencentQuote", "{\"stockCodes\":\"600519\"}");
            // 可能返回网络错误，但不应返回参数解析错误
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("多个股票代码")
        void multipleStocks() {
            String result = tool.a_stock_quote("tencentQuote", "{\"stockCodes\":\"600519,000858\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("缺少stockCodes参数 → 返回错误")
        void missingStockCodes() {
            String result = tool.a_stock_quote("tencentQuote", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }
    }

    @Nested
    @DisplayName("baiduKline K线查询")
    class BaiduKlineTest {

        @Test
        @DisplayName("标准查询")
        void standardQuery() {
            String result = tool.a_stock_quote("baiduKline", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("指定开始时间")
        void withStartTime() {
            String result = tool.a_stock_quote("baiduKline",
                    "{\"stockCode\":\"600519\",\"startTime\":\"2024-01-01\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_quote("baiduKline", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_quote("tencentQuote", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }

        @Test
        @DisplayName("空params → 正常处理")
        void emptyParams() {
            String result = tool.a_stock_quote("tencentQuote", "");
            assertNotNull(result, "空params不应崩溃");
        }

        @Test
        @DisplayName("null params → 正常处理")
        void nullParams() {
            String result = tool.a_stock_quote("tencentQuote", null);
            assertNotNull(result, "null params不应崩溃");
        }
    }
}
