package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.PlanningProperties;
import com.xiaomo.agent.agent.intent.AnalysisDepth;
import com.xiaomo.agent.agent.intent.ExecutionMode;
import com.xiaomo.agent.agent.intent.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskPlannerTest {

    private TaskPlanner planner;
    private PlanningProperties enabledProps;
    private PlanningProperties disabledProps;

    @BeforeEach
    void setUp() {
        enabledProps = new PlanningProperties(true, 5, 1024, 200);
        disabledProps = new PlanningProperties(false, 5, 1024, 200);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);

        planner = new TaskPlanner(builder);
    }

    @Nested
    @DisplayName("determineExecutionMode — 禁用时返回 DIRECT")
    class 禁用测试 {

        @Test
        void planning禁用时返回DIRECT() {
            injectPlanningProperties(disabledProps);
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("从估值和基本面分析茅台", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void null消息返回DIRECT() {
            injectPlanningProperties(enabledProps);
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode(null, IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 空消息返回DIRECT() {
            injectPlanningProperties(enabledProps);
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("  ", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void null意图返回DIRECT() {
            injectPlanningProperties(enabledProps);
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("分析茅台", null, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("determineExecutionMode — DEEP 深度分析始终 PLANNING")
    class 深度分析 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @Test
        void 深度分析个股返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("分析茅台", IntentType.STOCK_ANALYSIS, AnalysisDepth.DEEP));
        }

        @Test
        void 深度分析通用对话也返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("随便聊聊", IntentType.GENERAL_CHAT, AnalysisDepth.DEEP));
        }
    }

    @Nested
    @DisplayName("determineExecutionMode — DIRECT 场景")
    class 直接执行 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @Test
        void 单标的单维度返回DIRECT() {
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("茅台股价多少", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 简单问候返回DIRECT() {
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("你好", IntentType.GENERAL_CHAT, AnalysisDepth.NORMAL));
        }

        @Test
        void 金融计算返回DIRECT() {
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("帮我算一下收益率", IntentType.FINANCIAL_CALC, AnalysisDepth.NORMAL));
        }

        @Test
        void 单维度查询返回DIRECT() {
            assertEquals(ExecutionMode.DIRECT,
                    planner.determineExecutionMode("茅台PE是多少", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("determineExecutionMode — PARALLEL 场景")
    class 并行执行 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @Test
        void 多标的单维度返回PARALLEL() {
            // 两个标的、一个维度：可以并行查询
            assertEquals(ExecutionMode.PARALLEL,
                    planner.determineExecutionMode("茅台和五粮液的PE", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 两维度无综合需求返回PARALLEL() {
            // 两个维度、无综合决策需求：可以并行查询
            assertEquals(ExecutionMode.PARALLEL,
                    planner.determineExecutionMode("帮我看看茅台的ROE和PB", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 对比单维度返回PARALLEL() {
            // 对比两个标的但只有一个维度
            assertEquals(ExecutionMode.PARALLEL,
                    planner.determineExecutionMode("茅台和五粮液哪个PE更低", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("determineExecutionMode — PLANNING 场景")
    class 规划执行 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @Test
        void 依赖步骤返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("先找低估银行股，再分析基本面最好的三只",
                            IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 先再模式返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("先查板块排名，然后再分析龙头股",
                            IntentType.SECTOR_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 多标的多维度返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("从估值、盈利和资金面比较茅台与五粮液",
                            IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 综合决策需求返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("结合估值和基本面判断茅台是否值得买入",
                            IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 推荐需求返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("分析茅台的估值和财务，给出投资建议",
                            IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 三个以上子目标返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("分析茅台的估值。研究行业趋势。判断投资风险",
                            IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 根据结果继续执行返回PLANNING() {
            assertEquals(ExecutionMode.PLANNING,
                    planner.determineExecutionMode("找出资金流入明显的板块，根据结果分析龙头股",
                            IntentType.SECTOR_ANALYSIS, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("extractFeatures — 特征提取")
    class 特征提取 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @Test
        void 多维度关键词正确计数() {
            var features = planner.extractFeatures("从估值、基本面、资金面分析茅台");
            assertEquals(3, features.dimensionCount());
        }

        @Test
        void 依赖步骤正确识别() {
            var features = planner.extractFeatures("先查板块排名，然后再分析龙头股");
            assertTrue(features.hasDependentSteps());
        }

        @Test
        void 综合决策正确识别() {
            var features = planner.extractFeatures("判断茅台是否值得买入");
            assertTrue(features.hasSynthesisRequirement());
        }

        @Test
        void 对比需求正确识别() {
            var features = planner.extractFeatures("对比茅台和五粮液");
            assertTrue(features.hasComparisonRequirement());
        }

        @Test
        void 多标的正确识别() {
            var features = planner.extractFeatures("茅台和五粮液的PE");
            assertEquals(2, features.targetCount());
        }
    }

    /**
     * 通过反射注入 PlanningProperties，因为 TaskPlanner 使用 @Resource 注入。
     */
    private void injectPlanningProperties(PlanningProperties props) {
        try {
            var field = TaskPlanner.class.getDeclaredField("planningProperties");
            field.setAccessible(true);
            field.set(planner, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
