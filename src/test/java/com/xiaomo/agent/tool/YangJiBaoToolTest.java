package com.xiaomo.agent.tool;

import com.xiaomo.agent.yjb.entity.YjbAccountCollect;
import com.xiaomo.agent.yjb.entity.YjbHolding;
import com.xiaomo.agent.yjb.service.YjbService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("YangJiBaoTool 养基宝工具测试")
class YangJiBaoToolTest {

    @Mock
    private YjbService yjbService;

    private YangJiBaoTool tool;

    @BeforeEach
    void setUp() {
        tool = new YangJiBaoTool(yjbService);
    }

    @AfterEach
    void tearDown() {
        YangJiBaoTool.clearCurrentUserId();
    }

    @Nested
    @DisplayName("getMyHoldings 持仓查询")
    class GetMyHoldingsTest {

        @Test
        @DisplayName("未登录 → 返回错误")
        void notLoggedIn() {
            String result = tool.getMyHoldings();
            assertTrue(result.contains("无法获取用户身份"), "未登录应返回错误");
        }

        @Test
        @DisplayName("无持仓数据 → 返回提示")
        void noHoldings() {
            YangJiBaoTool.setCurrentUserId(1L);
            when(yjbService.getHoldings(1L)).thenReturn(Collections.emptyList());

            String result = tool.getMyHoldings();
            assertTrue(result.contains("暂无持仓数据"), "无持仓应返回提示");
        }

        @Test
        @DisplayName("有持仓数据 → 返回表格")
        void withHoldings() {
            YangJiBaoTool.setCurrentUserId(1L);

            YjbHolding holding = new YjbHolding();
            holding.setCode("110011");
            holding.setShortName("易方达中小盘");
            holding.setMoney(new BigDecimal("10000.00"));
            holding.setHoldEarn(new BigDecimal("500.00"));
            holding.setCostMoney(new BigDecimal("9500.00"));
            holding.setCategory("混合型");
            holding.setSyncedAt(LocalDateTime.now());

            when(yjbService.getHoldings(1L)).thenReturn(List.of(holding));

            String result = tool.getMyHoldings();
            assertTrue(result.contains("易方达中小盘"), "应包含基金名称");
            assertTrue(result.contains("110011"), "应包含基金代码");
            assertTrue(result.contains("10000.00"), "应包含持有市值");
            assertTrue(result.contains("混合型"), "应包含基金类型");
        }

        @Test
        @DisplayName("多只基金 → 显示汇总")
        void multipleHoldings() {
            YangJiBaoTool.setCurrentUserId(1L);

            YjbHolding h1 = new YjbHolding();
            h1.setCode("110011");
            h1.setShortName("基金A");
            h1.setMoney(new BigDecimal("10000.00"));
            h1.setHoldEarn(new BigDecimal("500.00"));
            h1.setCostMoney(new BigDecimal("9500.00"));
            h1.setCategory("混合型");
            h1.setSyncedAt(LocalDateTime.now());

            YjbHolding h2 = new YjbHolding();
            h2.setCode("161725");
            h2.setShortName("基金B");
            h2.setMoney(new BigDecimal("20000.00"));
            h2.setHoldEarn(new BigDecimal("-1000.00"));
            h2.setCostMoney(new BigDecimal("21000.00"));
            h2.setCategory("股票型");
            h2.setSyncedAt(LocalDateTime.now());

            when(yjbService.getHoldings(1L)).thenReturn(List.of(h1, h2));

            String result = tool.getMyHoldings();
            assertTrue(result.contains("共 2 只"), "应显示基金数量");
            assertTrue(result.contains("基金A"), "应包含基金A");
            assertTrue(result.contains("基金B"), "应包含基金B");
            assertTrue(result.contains("汇总"), "应有汇总信息");
        }
    }

    @Nested
    @DisplayName("getMyAccountSummary 账户汇总")
    class GetMyAccountSummaryTest {

        @Test
        @DisplayName("未登录 → 返回错误")
        void notLoggedIn() {
            String result = tool.getMyAccountSummary();
            assertTrue(result.contains("无法获取用户身份"), "未登录应返回错误");
        }

        @Test
        @DisplayName("无账户数据 → 返回提示")
        void noAccountData() {
            YangJiBaoTool.setCurrentUserId(1L);
            when(yjbService.getAccountSummary(1L)).thenReturn(Collections.emptyList());

            String result = tool.getMyAccountSummary();
            assertTrue(result.contains("暂无账户数据"), "无数据应返回提示");
        }

        @Test
        @DisplayName("有账户数据 → 返回汇总")
        void withAccountData() {
            YangJiBaoTool.setCurrentUserId(1L);

            YjbAccountCollect collect = new YjbAccountCollect();
            collect.setHoldCost(new BigDecimal("50000.00"));
            collect.setTodayIncome(new BigDecimal("200.00"));
            collect.setTodayIncomeRate(new BigDecimal("0.4000"));
            collect.setSyncedAt(LocalDateTime.now());

            when(yjbService.getAccountSummary(1L)).thenReturn(List.of(collect));

            String result = tool.getMyAccountSummary();
            assertTrue(result.contains("50000.00"), "应包含持有成本");
            assertTrue(result.contains("200.00"), "应包含今日收益");
            assertTrue(result.contains("0.4000"), "应包含今日收益率");
        }
    }

    @Nested
    @DisplayName("ThreadLocal 用户ID管理")
    class ThreadLocalTest {

        @Test
        @DisplayName("设置和清除用户ID")
        void setAndClear() {
            YangJiBaoTool.setCurrentUserId(123L);
            // 设置后不应报错
            when(yjbService.getHoldings(123L)).thenReturn(Collections.emptyList());
            String result = tool.getMyHoldings();
            assertFalse(result.contains("无法获取用户身份"));

            YangJiBaoTool.clearCurrentUserId();
            // 清除后应报错
            result = tool.getMyHoldings();
            assertTrue(result.contains("无法获取用户身份"));
        }
    }
}
