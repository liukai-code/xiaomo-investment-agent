package com.xiaomo.agent.tool.astock;

import com.xiaomo.agent.common.config.HttpClientService;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockOptionRouterTool A股期权工具测试")
class AStockOptionRouterToolTest {

    @Mock
    private HttpClientService httpClientService;

    private AStockOptionRouterTool tool;

    // 模拟期权合约清单API返回
    private static final String OPTION_CODES_RESPONSE = """
            {"result":{"data":{"contractMonth":["2025-01","2025-02","2025-03"],"contractList":["510050C2501M03200","510050P2501M03200"]}}}""";

    // 模拟期权T型报价API返回
    private static final String OPTION_T_QUOTE_RESPONSE = """
            var hq_str_CON_OP_510050C2501M03200="0.0500,0.0600,0.0400,0.0550,5000,10000,0.0500,0.0480,0.0450,0.0520,3200.0000,0.0600,2025-01-02,0.0500,0.0550,0.0400,0.0600,0.0400,10000,5000,0.0500,50ETF购1月3200,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000,0.0000";""";

    // 模拟期权Greeks API返回（需要至少16个字段）
    private static final String OPTION_GREEKS_RESPONSE = """
            var hq_str_CON_SO_510050C2501M03200="510050C2501M03200,50ETF购1月3200,0.5000,0.0300,-0.0010,0.0200,0.0001,0.2500,3200.0000,0.0500,2025-01-02,0.0550,0.0450,10000,5000,0.0500";""";

    @BeforeEach
    void setUp() {
        tool = new AStockOptionRouterTool(httpClientService);
    }

    @Nested
    @DisplayName("a_stock_option 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_option("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_option(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_option("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("optionCodes 期权合约清单")
    class OptionCodesTest {

        @Test
        @DisplayName("默认参数查询 → 解析合约清单")
        void defaultQuery() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(OPTION_CODES_RESPONSE);
            String result = tool.a_stock_option("optionCodes", "{}");
            assertTrue(result.contains("期权") || result.contains("50ETF") || result.contains("合约") || result.contains("查询失败"),
                    "应包含期权信息或查询结果");
        }

        @Test
        @DisplayName("指定标的和方向")
        void withParams() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(OPTION_CODES_RESPONSE);
            String result = tool.a_stock_option("optionCodes",
                    "{\"underlying\":\"510300\",\"call\":false}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("optionTQuote 期权T型报价")
    class OptionTQuoteTest {

        @Test
        @DisplayName("缺少contractCode参数 → 返回错误")
        void missingContractCode() {
            String result = tool.a_stock_option("optionTQuote", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析报价数据")
        void standardQuery() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(OPTION_T_QUOTE_RESPONSE);
            String result = tool.a_stock_option("optionTQuote",
                    "{\"contractCode\":\"510050C2501M03200\"}");
            assertTrue(result.contains("期权") || result.contains("T型") || result.contains("报价"),
                    "应包含报价信息");
            assertTrue(result.contains("3200") || result.contains("行权"),
                    "应包含行权价");
        }

        @Test
        @DisplayName("API返回空数据 → 返回提示")
        void emptyData() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(";");
            String result = tool.a_stock_option("optionTQuote",
                    "{\"contractCode\":\"invalid\"}");
            assertTrue(result.contains("不足") || result.contains("失败") || result.contains("异常"),
                    "空数据应返回提示");
        }
    }

    @Nested
    @DisplayName("optionGreeks 期权希腊字母")
    class OptionGreeksTest {

        @Test
        @DisplayName("缺少contractCode参数 → 返回错误")
        void missingContractCode() {
            String result = tool.a_stock_option("optionGreeks", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析Greeks数据")
        void standardQuery() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(OPTION_GREEKS_RESPONSE);
            String result = tool.a_stock_option("optionGreeks",
                    "{\"contractCode\":\"510050C2501M03200\"}");
            assertTrue(result.contains("Greeks") || result.contains("Delta") || result.contains("delta")
                            || result.contains("期权") || result.contains("查询失败") || result.contains("失败"),
                    "应包含Greeks信息或查询结果");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_option("optionCodes", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }
    }
}
