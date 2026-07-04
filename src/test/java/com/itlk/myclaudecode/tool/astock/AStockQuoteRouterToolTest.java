package com.itlk.myclaudecode.tool.astock;

import com.itlk.myclaudecode.common.config.HttpClientService;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockQuoteRouterTool A股行情工具测试")
class AStockQuoteRouterToolTest {

    @Mock
    private HttpClientService httpClientService;

    private AStockQuoteRouterTool tool;

    // 模拟百度K线API返回
    private static final String BAIDU_KLINE_RESPONSE = """
            {"QueryID":"123","ResultCode":"0","Result":{"newMarketData":{"headers":["timestamp","time","open","close","volume","high","low","amount","range","ratio","turnoverratio","preClose","ma5avgprice","ma5volume","ma10avgprice","ma10volume","ma20avgprice","ma20volume"],"keys":["timestamp","time","open","close","volume","high","low","amount","range","ratio","turnoverratio","preClose","ma5avgprice","ma5volume","ma10avgprice","ma10volume","ma20avgprice","ma20volume"],"marketData":"1735747200,2025-01-02,1444.42,1408.42,5002870,1444.91,1400.42,7490883773.00,-36.00,-2.49,0.40,1444.42,1420.50,4500000,1430.20,4200000,1440.10,4000000;1735833600,2025-01-03,1414.92,1395.42,3262836,1415.41,1387.43,4836610288.00,-13.00,-0.92,0.26,1408.42,1405.60,4300000,1420.80,4100000,1435.50,3900000"}}}}""";

    // 模拟百度K线API返回403
    private static final String BAIDU_KLINE_403 = """
            {"QueryID":"0","ResultCode":"403","Result":[]}""";

    // 模拟腾讯行情API返回
    private static final String TENCENT_QUOTE_RESPONSE = """
            v_sh600519="51~贵州茅台~600519~1800.00~1790.00~1785.00~50000~25000~25000~1800.00~100~1799.90~50~1799.80~30~1799.70~20~1799.60~10~1800.10~80~1800.20~60~1800.30~40~1800.40~20~1800.50~10~~20250102150000~10.00~0.56~1810.00~1780.00~1800.00/50000/9000000000~50000~90000~1.50~25.50~~1810.00~1780.00~1.68~22600.00~22600.00~2.50~1970.00~1610.00~0.80~1790~1800.00~18.50~50.00~~~1.20~90000000.00~50000.00~20~GP-A~0.13~-0.10~-0.50~29.50~15.20~22500.00~22600.00~7.80~6.50~1970.00~281851892~423061007~60.50~25.00~281851892~~~1805.00~-0.20~~CNY~0~~1799.90~5~";""";

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
        @DisplayName("单个股票代码 → 解析行情数据")
        void singleStock() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(TENCENT_QUOTE_RESPONSE);
            String result = tool.a_stock_quote("tencentQuote", "{\"stockCodes\":\"600519\"}");
            assertTrue(result.contains("贵州茅台"), "应包含股票名称");
            assertTrue(result.contains("600519"), "应包含股票代码");
            assertTrue(result.contains("1800.00") || result.contains("1800"), "应包含价格");
        }

        @Test
        @DisplayName("多个股票代码")
        void multipleStocks() throws Exception {
            String multiResponse = TENCENT_QUOTE_RESPONSE + """
                    v_sz000858="51~五粮液~000858~150.00~149.00~148.00~30000~15000~15000~150.00~50~149.90~30~149.80~20~149.70~10~149.60~5~150.10~40~150.20~30~150.30~20~150.40~10~150.50~5~~20250102150000~1.00~0.67~152.00~147.00~150.00/30000/4500000000~30000~45000~2.00~30.00~~152.00~147.00~3.36~5600.00~5600.00~2.00~164.00~134.00~0.90~149~150.00~10.00~20.00~~~0.80~45000000.00~30000.00~15~GP-A~0.10~-0.05~-0.30~35.00~18.00~5500.00~5600.00~6.50~5.50~134.00~386477897~483200000~45.00~20.00~386477897~~~150.50~-0.10~~CNY~0~~149.90~3~";""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(multiResponse);
            String result = tool.a_stock_quote("tencentQuote", "{\"stockCodes\":\"600519,000858\"}");
            assertTrue(result.contains("贵州茅台"), "应包含第一只股票");
            assertTrue(result.contains("五粮液"), "应包含第二只股票");
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
        @DisplayName("标准查询 → 解析K线数据和MA均线")
        void standardQuery() throws Exception {
            when(httpClientService.getWithJdkClient(anyString(), any()))
                    .thenReturn(BAIDU_KLINE_RESPONSE);
            String result = tool.a_stock_quote("baiduKline", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("K线数据"), "应包含K线标题");
            assertTrue(result.contains("开:") && result.contains("收:"),
                    "应包含开盘价和收盘价");
            assertTrue(result.contains("1444") || result.contains("1408") || result.contains("1414"),
                    "应包含实际价格数据");
            assertTrue(result.contains("共") && result.contains("K线"),
                    "应包含K线总数");
        }

        @Test
        @DisplayName("指定开始时间")
        void withStartTime() throws Exception {
            when(httpClientService.getWithJdkClient(anyString(), any()))
                    .thenReturn(BAIDU_KLINE_RESPONSE);
            String result = tool.a_stock_quote("baiduKline",
                    "{\"stockCode\":\"600519\",\"startTime\":\"2024-01-01\"}");
            assertTrue(result.contains("K线数据"), "应返回K线数据");
        }

        @Test
        @DisplayName("API返回403 → 返回错误信息含resultCode")
        void apiReturn403() throws Exception {
            when(httpClientService.getWithJdkClient(anyString(), any()))
                    .thenReturn(BAIDU_KLINE_403);
            String result = tool.a_stock_quote("baiduKline", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("403") || result.contains("未找到"),
                    "应返回403错误信息");
            assertFalse(result.contains("K线数据（百度）"), "403不应返回正常K线标题");
        }

        @Test
        @DisplayName("API返回空marketData → 返回未找到")
        void apiReturnEmptyData() throws Exception {
            String emptyResponse = """
                    {"QueryID":"123","ResultCode":"0","Result":{"newMarketData":{"marketData":""}}}""";
            when(httpClientService.getWithJdkClient(anyString(), any()))
                    .thenReturn(emptyResponse);
            String result = tool.a_stock_quote("baiduKline", "{\"stockCode\":\"999999\"}");
            assertTrue(result.contains("未找到"), "空数据应返回未找到");
        }

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_quote("baiduKline", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("网络异常 → 返回失败信息")
        void networkError() throws Exception {
            when(httpClientService.getWithJdkClient(anyString(), any()))
                    .thenThrow(new RuntimeException("Connection refused"));
            String result = tool.a_stock_quote("baiduKline", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("失败") || result.contains("异常"),
                    "网络异常应返回失败信息");
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
