package com.xiaomo.agent.tool.astock;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockNewsRouterTool A股新闻工具测试")
class AStockNewsRouterToolTest {

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    private AStockNewsRouterTool tool;

    private static final String STOCK_NEWS_RESPONSE = """
            {"result":{"data":[{"title":"贵州茅台发布年度报告","showTime":"2025-01-02","url":"https://finance.eastmoney.com/a/2025010212345.html"},{"title":"白酒板块集体上涨","showTime":"2025-01-02","url":"https://finance.eastmoney.com/a/2025010212346.html"}]}}""";

    private static final String GLOBAL_NEWS_RESPONSE = """
            {"result":{"data":[{"title":"美股三大指数收涨","showTime":"2025-01-02","url":"https://finance.eastmoney.com/a/2025010212347.html"}]}}""";

    private static final String ANNOUNCEMENT_RESPONSE = """
            {"result":{"data":[{"announcementTitle":"贵州茅台2024年年度报告","announcementTime":"2025-01-02","adjunctUrl":"http://www.cninfo.com.cn/new/disclosure/detail?announcementId=12345"}]}}""";

    @BeforeEach
    void setUp() {
        tool = new AStockNewsRouterTool(emRateLimiter);
    }

    @Nested
    @DisplayName("a_stock_news 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_news("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_news(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_news("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("stockNews 个股新闻")
    class StockNewsTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_news("stockNews", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析新闻数据")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(STOCK_NEWS_RESPONSE);
            String result = tool.a_stock_news("stockNews", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("茅台") || result.contains("新闻") || result.contains("报告"),
                    "应包含新闻标题或关键词");
        }

        @Test
        @DisplayName("API返回空数据 → 返回提示")
        void emptyData() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[]}}");
            String result = tool.a_stock_news("stockNews", "{\"stockCode\":\"999999\"}");
            assertTrue(result.contains("未找到") || result.contains("没有") || result.contains("0") || result.contains("新闻"),
                    "空数据应返回提示");
        }
    }

    @Nested
    @DisplayName("globalNews 全球资讯")
    class GlobalNewsTest {

        @Test
        @DisplayName("默认参数查询 → 解析资讯")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(GLOBAL_NEWS_RESPONSE);
            String result = tool.a_stock_news("globalNews", "{}");
            assertTrue(result.contains("美股") || result.contains("资讯") || result.contains("新闻"),
                    "应包含资讯内容");
        }
    }

    @Nested
    @DisplayName("cninfoAnnouncements 巨潮公告")
    class CninfoAnnouncementsTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_news("cninfoAnnouncements", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析公告数据")
        void standardQuery() throws Exception {
            when(emRateLimiter.post(anyString(), anyString(), any()))
                    .thenReturn(ANNOUNCEMENT_RESPONSE);
            String result = tool.a_stock_news("cninfoAnnouncements", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("公告") || result.contains("茅台") || result.contains("报告"),
                    "应包含公告信息");
        }
    }

    @Nested
    @DisplayName("irmQA 互动易问答")
    class IrmQATest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_news("irmQA", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            lenient().when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[{\"question\":\"公司业绩如何\",\"answer\":\"感谢关注\",\"askDate\":\"2025-01-02\"}]}}");
            lenient().when(emRateLimiter.post(anyString(), anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[{\"question\":\"公司业绩如何\",\"answer\":\"感谢关注\",\"askDate\":\"2025-01-02\"}]}}");
            String result = tool.a_stock_news("irmQA", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("sinaFinancialReport 新浪财报")
    class SinaFinancialReportTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_news("sinaFinancialReport", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询")
        void standardQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":{\"report\":[{\"date\":\"2024-09-30\",\"revenue\":\"1000000000\"}]}}}");
            String result = tool.a_stock_news("sinaFinancialReport", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_news("stockNews", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }
    }
}
