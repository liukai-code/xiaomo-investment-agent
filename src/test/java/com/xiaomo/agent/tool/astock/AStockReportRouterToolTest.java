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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockReportRouterTool A股研报工具测试")
class AStockReportRouterToolTest {

    @Mock
    private HttpClientService httpClientService;

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    private AStockReportRouterTool tool;

    // 模拟东财个股研报API返回
    private static final String STOCK_REPORT_RESPONSE = """
            {"result":{"pages":1,"data":[{"stockName":"贵州茅台","title":"贵州茅台深度报告","orgSName":"中信证券","publishDate":"2025-01-02","infoCode":"R12345","emRatingName":"买入","predictThisYearPe":"25.00","predictThisYearEps":"60.00","indvInduName":"白酒"}],"count":1}}""";

    // 模拟东财行业研报API返回
    private static final String INDUSTRY_REPORT_RESPONSE = """
            {"result":{"pages":1,"data":[{"indvInduName":"白酒","title":"白酒行业年度策略","orgSName":"中信证券","publishDate":"2025-01-02","infoCode":"R67890"}],"count":1}}""";

    @BeforeEach
    void setUp() {
        tool = new AStockReportRouterTool(httpClientService, emRateLimiter, "");
    }

    @Nested
    @DisplayName("a_stock_report 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_report("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_report(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_report("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("stockReport 个股研报")
    class StockReportTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_report("stockReport", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析研报数据")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(STOCK_REPORT_RESPONSE);
            String result = tool.a_stock_report("stockReport", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("贵州茅台") || result.contains("研报"),
                    "应包含股票名称或研报标题");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }

        @Test
        @DisplayName("API返回空数据 → 返回提示")
        void emptyData() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"pages\":0,\"data\":[],\"count\":0}}");
            String result = tool.a_stock_report("stockReport", "{\"stockCode\":\"999999\"}");
            assertTrue(result.contains("未找到") || result.contains("没有") || result.contains("0"),
                    "空数据应返回提示");
        }
    }

    @Nested
    @DisplayName("industryReport 行业研报")
    class IndustryReportTest {

        @Test
        @DisplayName("全行业查询 → 解析行业研报")
        void allIndustries() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(INDUSTRY_REPORT_RESPONSE);
            String result = tool.a_stock_report("industryReport", "{\"industryCode\":\"*\"}");
            assertTrue(result.contains("白酒") || result.contains("研报") || result.contains("行业"),
                    "应包含行业信息");
        }

        @Test
        @DisplayName("指定行业码")
        void specificIndustry() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(INDUSTRY_REPORT_RESPONSE);
            String result = tool.a_stock_report("industryReport", "{\"industryCode\":\"1238\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("downloadReportPdf 研报PDF")
    class DownloadReportPdfTest {

        @Test
        @DisplayName("生成PDF链接")
        void generatePdfLink() {
            String result = tool.a_stock_report("downloadReportPdf", "{\"infoCode\":\"12345\"}");
            assertTrue(result.contains("pdf.dfcfw.com"), "应返回PDF链接");
        }

        @Test
        @DisplayName("缺少infoCode参数 → 返回错误")
        void missingInfoCode() {
            String result = tool.a_stock_report("downloadReportPdf", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }
    }

    @Nested
    @DisplayName("iwencaiSearch 语义搜索")
    class IwencaiSearchTest {

        @Test
        @DisplayName("未配置API Key → 返回提示")
        void noApiKey() {
            String result = tool.a_stock_report("iwencaiSearch", "{\"query\":\"贵州茅台\"}");
            assertTrue(result.contains("API Key") || result.contains("未配置"),
                    "未配置API Key应返回提示");
        }
    }

    @Nested
    @DisplayName("iwencaiQuery 结构化查询")
    class IwencaiQueryTest {

        @Test
        @DisplayName("未配置API Key → 返回提示")
        void noApiKey() {
            String result = tool.a_stock_report("iwencaiQuery", "{\"query\":\"市盈率\"}");
            assertTrue(result.contains("API Key") || result.contains("未配置"),
                    "未配置API Key应返回提示");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_report("stockReport", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }

        @Test
        @DisplayName("空params → 正常处理")
        void emptyParams() {
            String result = tool.a_stock_report("stockReport", "");
            assertNotNull(result, "空params不应崩溃");
        }

        @Test
        @DisplayName("null params → 正常处理")
        void nullParams() {
            String result = tool.a_stock_report("stockReport", null);
            assertNotNull(result, "null params不应崩溃");
        }
    }
}
