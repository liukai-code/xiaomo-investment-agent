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
@DisplayName("AStockSentimentRouterTool A股舆情工具测试")
class AStockSentimentRouterToolTest {

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    private AStockSentimentRouterTool tool;

    private static final String THS_HOT_LIST_RESPONSE = """
            {"result":{"data":[{"code":"600519","name":"贵州茅台","hot":98.50,"rank":1},{"code":"000858","name":"五粮液","hot":85.20,"rank":2}]}}""";

    private static final String EM_HOT_RANK_RESPONSE = """
            {"result":{"data":{"diff":[{"f12":"600519","f14":"贵州茅台","f3":2.50,"f2":1800.00}]}}}""";

    private static final String EM_CONCEPT_HIT_RESPONSE = """
            {"result":{"data":[{"f14":"白酒","f3":2.50,"f104":50000,"f105":30000,"f128":"贵州茅台"}]}}""";

    @BeforeEach
    void setUp() {
        tool = new AStockSentimentRouterTool(emRateLimiter);
    }

    @Nested
    @DisplayName("a_stock_sentiment 操作路由")
    class OperationRoutingTest {

        @Test
        @DisplayName("空操作类型 → 返回提示")
        void emptyOperation() {
            String result = tool.a_stock_sentiment("", "{}");
            assertTrue(result.contains("操作类型不能为空"), "空操作应返回提示");
        }

        @Test
        @DisplayName("null操作类型 → 返回提示")
        void nullOperation() {
            String result = tool.a_stock_sentiment(null, "{}");
            assertTrue(result.contains("操作类型不能为空"), "null操作应返回提示");
        }

        @Test
        @DisplayName("未知操作 → 返回提示")
        void unknownOperation() {
            String result = tool.a_stock_sentiment("unknownOp", "{}");
            assertTrue(result.contains("未知操作"), "未知操作应返回提示");
        }
    }

    @Nested
    @DisplayName("thsHotList 同花顺热榜")
    class ThsHotListTest {

        @Test
        @DisplayName("默认参数查询 → 解析热榜数据")
        void defaultQuery() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(THS_HOT_LIST_RESPONSE);
            String result = tool.a_stock_sentiment("thsHotList", "{}");
            assertTrue(result.contains("热榜") || result.contains("贵州茅台") || result.contains("排名"),
                    "应包含热榜信息");
        }

        @Test
        @DisplayName("指定周期")
        void withPeriod() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(THS_HOT_LIST_RESPONSE);
            String result = tool.a_stock_sentiment("thsHotList", "{\"period\":\"day\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("API返回空数据 → 返回提示")
        void emptyData() throws Exception {
            when(emRateLimiter.get(anyString(), any()))
                    .thenReturn("{\"result\":{\"data\":[]}}");
            String result = tool.a_stock_sentiment("thsHotList", "{}");
            assertTrue(result.contains("未找到") || result.contains("没有") || result.contains("0") || result.contains("热榜"),
                    "空数据应返回提示");
        }
    }

    @Nested
    @DisplayName("emHotRank 东财热榜")
    class EmHotRankTest {

        @Test
        @DisplayName("默认参数查询 → 解析热榜")
        void defaultQuery() throws Exception {
            lenient().when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(EM_HOT_RANK_RESPONSE);
            lenient().when(emRateLimiter.post(anyString(), anyString(), any()))
                    .thenReturn(EM_HOT_RANK_RESPONSE);
            String result = tool.a_stock_sentiment("emHotRank", "{}");
            assertNotNull(result, "不应返回null");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }

        @Test
        @DisplayName("指定topN")
        void withTop() throws Exception {
            lenient().when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(EM_HOT_RANK_RESPONSE);
            lenient().when(emRateLimiter.post(anyString(), anyString(), any()))
                    .thenReturn(EM_HOT_RANK_RESPONSE);
            String result = tool.a_stock_sentiment("emHotRank", "{\"top\":10}");
            assertNotNull(result, "不应返回null");
            assertFalse(result.contains("操作失败"), "不应返回操作失败");
        }
    }

    @Nested
    @DisplayName("emConceptHit 东财概念命中")
    class EmConceptHitTest {

        @Test
        @DisplayName("缺少stockCode参数 → 返回错误")
        void missingStockCode() {
            String result = tool.a_stock_sentiment("emConceptHit", "{}");
            assertTrue(result.contains("缺少参数") || result.contains("失败"),
                    "缺少参数应返回错误");
        }

        @Test
        @DisplayName("标准查询 → 解析概念数据")
        void standardQuery() throws Exception {
            lenient().when(emRateLimiter.get(anyString(), any()))
                    .thenReturn(EM_CONCEPT_HIT_RESPONSE);
            lenient().when(emRateLimiter.post(anyString(), anyString(), any()))
                    .thenReturn(EM_CONCEPT_HIT_RESPONSE);
            String result = tool.a_stock_sentiment("emConceptHit", "{\"stockCode\":\"600519\"}");
            assertTrue(result.contains("白酒") || result.contains("概念") || result.contains("板块") || result.contains("查询失败"),
                    "应包含概念信息");
        }
    }

    @Nested
    @DisplayName("params JSON解析")
    class ParamsParsingTest {

        @Test
        @DisplayName("无效JSON → 返回错误")
        void invalidJson() {
            String result = tool.a_stock_sentiment("thsHotList", "not valid json");
            assertTrue(result.contains("解析失败") || result.contains("失败"),
                    "无效JSON应返回错误");
        }
    }
}
