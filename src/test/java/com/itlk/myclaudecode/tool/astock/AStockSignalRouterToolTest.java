package com.itlk.myclaudecode.tool.astock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AStockSignalRouterTool A股信号工具测试")
class AStockSignalRouterToolTest {

    @Mock
    private EastMoneyRateLimiter emRateLimiter;

    private AStockSignalRouterTool tool;

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
        @DisplayName("标准查询")
        void standardQuery() {
            String result = tool.a_stock_signal("conceptBlocks", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
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
        void standardQuery() {
            String result = tool.a_stock_signal("fundFlowMinute", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
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
        @DisplayName("标准查询")
        void standardQuery() {
            String result = tool.a_stock_signal("dragonTigerBoard",
                    "{\"stockCode\":\"600519\",\"tradeDate\":\"2024-01-01\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("自定义回溯天数")
        void withLookBackDays() {
            String result = tool.a_stock_signal("dragonTigerBoard",
                    "{\"stockCode\":\"600519\",\"lookBackDays\":60}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("dailyDragonTiger 全市场龙虎榜")
    class DailyDragonTigerTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() {
            String result = tool.a_stock_signal("dailyDragonTiger", "{}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("指定日期")
        void withDate() {
            String result = tool.a_stock_signal("dailyDragonTiger",
                    "{\"tradeDate\":\"2024-01-01\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("指定最低净买入")
        void withMinNetBuy() {
            String result = tool.a_stock_signal("dailyDragonTiger",
                    "{\"minNetBuy\":1000}");
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
        void standardQuery() {
            String result = tool.a_stock_signal("lockupExpiry", "{\"stockCode\":\"600519\"}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("自定义前瞻天数")
        void withForwardDays() {
            String result = tool.a_stock_signal("lockupExpiry",
                    "{\"stockCode\":\"600519\",\"forwardDays\":180}");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("industryRanking 行业排名")
    class IndustryRankingTest {

        @Test
        @DisplayName("默认参数查询")
        void defaultQuery() {
            String result = tool.a_stock_signal("industryRanking", "{}");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("自定义topN")
        void withTopN() {
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
