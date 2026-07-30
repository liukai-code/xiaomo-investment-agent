package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.PlanningProperties;
import com.xiaomo.agent.agent.intent.AnalysisDepth;
import com.xiaomo.agent.agent.intent.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @DisplayName("needsPlanning — 不需要规划的意图")
    class 不需要规划 {

        @ParameterizedTest
        @EnumSource(value = IntentType.class, names = {
                "GENERAL_CHAT", "FINANCIAL_CALC", "DB_QUERY", "HOLDINGS_QUERY"
        })
        void 简单意图返回False(IntentType intent) {
            injectPlanningProperties(enabledProps);
            assertFalse(planner.needsPlanning("随便什么消息", intent, AnalysisDepth.NORMAL));
        }

        @Test
        void planning禁用时所有意图返回False() {
            injectPlanningProperties(disabledProps);
            assertFalse(planner.needsPlanning("从估值和基本面分析茅台", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void null消息返回False() {
            injectPlanningProperties(enabledProps);
            assertFalse(planner.needsPlanning(null, IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 空消息返回False() {
            injectPlanningProperties(enabledProps);
            assertFalse(planner.needsPlanning("  ", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void null意图返回False() {
            injectPlanningProperties(enabledProps);
            assertFalse(planner.needsPlanning("分析茅台", null, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("needsPlanning — 深度分析始终触发规划")
    class 深度分析 {

        @Test
        void 深度分析始终返回True() {
            injectPlanningProperties(enabledProps);
            assertTrue(planner.needsPlanning("分析茅台", IntentType.STOCK_ANALYSIS, AnalysisDepth.DEEP));
        }

        @Test
        void 深度分析通用对话也触发规划() {
            injectPlanningProperties(enabledProps);
            assertTrue(planner.needsPlanning("随便聊聊", IntentType.GENERAL_CHAT, AnalysisDepth.DEEP));
        }
    }

    @Nested
    @DisplayName("needsPlanning — STOCK_ANALYSIS 多维度判断")
    class 个股分析多维度 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "从估值、基本面、资金面三个角度分析茅台",
                "分析茅台的估值和财务情况",
                "帮我看看茅台的PE和ROE",
                "茅台的技术面和资金流怎么样"
        })
        void 多维度关键词返回True(String msg) {
            assertTrue(planner.needsPlanning(msg, IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 包含角度一词返回True() {
            assertTrue(planner.needsPlanning("从不同角度分析茅台", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 单一维度返回False() {
            assertFalse(planner.needsPlanning("茅台股价多少", IntentType.STOCK_ANALYSIS, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("needsPlanning — MARKET_NEWS 多主题判断")
    class 市场新闻多主题 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @Test
        void 多个主题关键词返回True() {
            assertTrue(planner.needsPlanning("今天大盘和央行的新闻", IntentType.MARKET_NEWS, AnalysisDepth.NORMAL));
        }

        @Test
        void 单主题返回False() {
            assertFalse(planner.needsPlanning("最近有什么新闻", IntentType.MARKET_NEWS, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("needsPlanning — SECTOR_ANALYSIS 对比判断")
    class 板块分析对比 {

        @BeforeEach
        void setup() {
            injectPlanningProperties(enabledProps);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "对比新能源和半导体板块",
                "新能源和半导体哪个好",
                "新能源 vs 半导体"
        })
        void 包含对比词返回True(String msg) {
            assertTrue(planner.needsPlanning(msg, IntentType.SECTOR_ANALYSIS, AnalysisDepth.NORMAL));
        }

        @Test
        void 无对比词返回False() {
            assertFalse(planner.needsPlanning("新能源板块怎么样", IntentType.SECTOR_ANALYSIS, AnalysisDepth.NORMAL));
        }
    }

    @Nested
    @DisplayName("needsPlanning — TRADING_SENTIMENT")
    class 打板情绪 {

        @Test
        void 打板情绪始终返回False() {
            injectPlanningProperties(enabledProps);
            assertFalse(planner.needsPlanning("今天涨停多少家", IntentType.TRADING_SENTIMENT, AnalysisDepth.NORMAL));
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
